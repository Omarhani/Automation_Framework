#!/usr/bin/env node
/*
 * Drives the browser web/webOpenLoggedIn left open (Chrome / Edge DevTools on 127.0.0.1:<port>), so a session the
 * framework logged in (its code typed the password from data/testData.json) can be used afterwards - to walk a
 * scenario step by step in that same session, read texts, take screenshots. No packages: Node 22+ (global
 * WebSocket + fetch).
 *
 *   node tools/browser.js url                         current page url + title
 *   node tools/browser.js goto /orders                open a path of the app (or a full url)
 *   node tools/browser.js text [selector]             visible text of the page / of one element
 *   node tools/browser.js click <selector>            real mouse click in the middle of the element
 *   node tools/browser.js type <selector> <text>      click the field, clear it, type the text
 *   node tools/browser.js key Enter|Tab|Escape        press a key
 *   node tools/browser.js wait <selector> [seconds]   wait until the element is visible (default 15 s)
 *   node tools/browser.js shot [file.png]             screenshot (default target/browser-shot.png)
 *   node tools/browser.js eval "<js expression>"      value of a JS expression on the page
 *   node tools/browser.js close                       close the browser + stop leftover drivers
 *   node tools/browser.js cleanup                     only stop leftover drivers (run over, parent gone)
 *
 * Git Bash: run with MSYS_NO_PATHCONV=1, else it turns "/orders" or "//table" into C:/Program Files/Git/... paths.
 *
 * selector: XPath when it starts with / or ( - else CSS.  --port=9334 for another browser (default: the port in
 * target/open-browser.json, else 9333) - two actors = two browsers on two ports.  Cookies, tokens, passwords and
 * the stored login are not read out: eval refuses expressions that name them.
 */
const fs = require('fs');
const path = require('path');

const args = process.argv.slice(2).filter(a => !a.startsWith('--port='));
const portArg = process.argv.find(a => a.startsWith('--port='));
const sessionFile = path.join(__dirname, '..', 'target', 'open-browser.json');
const session = fs.existsSync(sessionFile) ? JSON.parse(fs.readFileSync(sessionFile, 'utf8')) : {};
const port = portArg ? portArg.split('=')[1] : (session.port || (session.debuggerAddress || '127.0.0.1:9333').split(':')[1]);
const base = `http://127.0.0.1:${port}`;

const KEYS = {
    Enter: {key: 'Enter', code: 'Enter', keyCode: 13, text: '\r'},
    Tab: {key: 'Tab', code: 'Tab', keyCode: 9},
    Escape: {key: 'Escape', code: 'Escape', keyCode: 27},
    Backspace: {key: 'Backspace', code: 'Backspace', keyCode: 8},
    ArrowDown: {key: 'ArrowDown', code: 'ArrowDown', keyCode: 40},
    ArrowUp: {key: 'ArrowUp', code: 'ArrowUp', keyCode: 38},
};

async function pageTarget() {
    let targets;
    try {
        targets = await (await fetch(`${base}/json`)).json();
    } catch (e) {
        throw new Error(`No browser on ${base} - run: mvn test -Dtestng=web/webOpenLoggedIn -DserverType=<env>`);
    }
    const pages = targets.filter(t => t.type === 'page');
    const app = session.baseUrl && pages.find(t => t.url.startsWith(session.baseUrl));
    const target = app || pages[0];
    if (!target) throw new Error(`No page open in the browser on ${base}`);
    return target;
}

function connect(wsUrl) {
    return new Promise((resolve, reject) => {
        const ws = new WebSocket(wsUrl);
        let id = 0;
        const pending = new Map();
        const listeners = [];
        ws.onmessage = m => {
            const msg = JSON.parse(m.data);
            if (msg.id && pending.has(msg.id)) {
                const {res, rej} = pending.get(msg.id);
                pending.delete(msg.id);
                msg.error ? rej(new Error(msg.error.message)) : res(msg.result);
            } else if (msg.method) {
                listeners.forEach(l => l(msg));
            }
        };
        ws.onerror = () => reject(new Error(`Could not attach to ${wsUrl}`));
        ws.onopen = () => resolve({
            send: (method, params = {}) => new Promise((res, rej) => {
                pending.set(++id, {res, rej});
                ws.send(JSON.stringify({id, method, params}));
            }),
            on: l => listeners.push(l),
            close: () => ws.close(),
        });
    });
}

/** JS that finds the element of a selector (XPath or CSS). */
function finder(selector) {
    const s = JSON.stringify(selector);
    return /^[/(]/.test(selector)
        ? `document.evaluate(${s}, document, null, XPathResult.FIRST_ORDERED_NODE_TYPE, null).singleNodeValue`
        : `document.querySelector(${s})`;
}

async function evaluate(cdp, expression) {
    const r = await cdp.send('Runtime.evaluate', {expression, returnByValue: true, awaitPromise: true});
    if (r.exceptionDetails) throw new Error(r.exceptionDetails.exception?.description || r.exceptionDetails.text);
    return r.result.value;
}

/** Middle of the visible element, waiting up to `seconds`. */
async function box(cdp, selector, seconds = 15) {
    const end = Date.now() + seconds * 1000;
    while (true) {
        const b = await evaluate(cdp, `(() => { const e = ${finder(selector)}; if (!e) return null;
            e.scrollIntoView({block: 'center', inline: 'center'}); const r = e.getBoundingClientRect();
            return r.width && r.height ? {x: r.x + r.width / 2, y: r.y + r.height / 2} : null; })()`);
        if (b) return b;
        if (Date.now() > end) throw new Error(`Not found / not visible after ${seconds} s: ${selector}`);
        await new Promise(r => setTimeout(r, 300));
    }
}

async function click(cdp, selector) {
    const {x, y} = await box(cdp, selector);
    for (const type of ['mouseMoved', 'mousePressed', 'mouseReleased']) {
        await cdp.send('Input.dispatchMouseEvent', {type, x, y, button: 'left', clickCount: 1});
    }
}

async function key(cdp, name) {
    const k = KEYS[name];
    if (!k) throw new Error(`Unknown key ${name} - one of ${Object.keys(KEYS).join(', ')}`);
    const base = {key: k.key, code: k.code, windowsVirtualKeyCode: k.keyCode};
    await cdp.send('Input.dispatchKeyEvent', {type: 'keyDown', ...base, ...(k.text ? {text: k.text} : {})});
    await cdp.send('Input.dispatchKeyEvent', {type: 'keyUp', ...base});
}

async function waitForLoad(cdp, seconds = 30) {
    const end = Date.now() + seconds * 1000;
    while (Date.now() < end) {
        if (await evaluate(cdp, 'document.readyState').catch(() => '') === 'complete') return;
        await new Promise(r => setTimeout(r, 300));
    }
}

/**
 * Stops the chromedriver / msedgedriver processes whose run is over (parent process gone). A driver whose Java run
 * is still going keeps its parent, so it is not touched.
 */
function stopOrphanDrivers() {
    if (process.platform !== 'win32') {
        // parent gone = re-parented to init (pid 1)
        const out = require('child_process').execFileSync('ps', ['-eo', 'pid=,ppid=,comm=']).toString();
        const stopped = out.split('\n').map(l => l.trim().split(/\s+/))
            .filter(([, ppid, comm]) => ppid === '1' && /(chromedriver|msedgedriver)$/.test(comm || ''))
            .map(([pid]) => {
                try { process.kill(Number(pid), 'SIGKILL'); } catch (e) { /* already gone */ }
                return pid;
            });
        console.log(stopped.length ? `stopped ${stopped.length} leftover driver(s): ${stopped.join(', ')}` : 'no leftover drivers');
        return;
    }
    const ps = `$all = Get-CimInstance Win32_Process; $ids = $all.ProcessId;
        $all | Where-Object { ($_.Name -eq 'chromedriver.exe' -or $_.Name -eq 'msedgedriver.exe') -and ($ids -notcontains $_.ParentProcessId) } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue; $_.ProcessId }`;
    const out = require('child_process').execFileSync('powershell', ['-NoProfile', '-Command', ps]).toString().trim();
    const stopped = out ? out.split(/\s+/) : [];
    console.log(stopped.length ? `stopped ${stopped.length} leftover driver(s): ${stopped.join(', ')}` : 'no leftover drivers');
}

async function main() {
    const [command, ...rest] = args;
    if (!command) {
        console.log(fs.readFileSync(__filename, 'utf8').split('*/')[0]);
        return;
    }
    if (command === 'close') {
        // Browser.close goes to the browser's own endpoint, not a page's
        try {
            await pageTarget();
            const browser = await connect((await (await fetch(`${base}/json/version`)).json()).webSocketDebuggerUrl);
            await browser.send('Browser.close').catch(() => {});
            browser.close();
            console.log('browser closed');
        } catch (e) {
            console.log(`no browser on ${base}`);
        }
        stopOrphanDrivers();
        return;
    }
    if (command === 'cleanup') {
        stopOrphanDrivers();
        return;
    }
    const target = await pageTarget();
    const cdp = await connect(target.webSocketDebuggerUrl);
    try {
        switch (command) {
            case 'url':
                console.log(JSON.stringify({url: await evaluate(cdp, 'location.href'), title: await evaluate(cdp, 'document.title'),
                    env: session.env, user: session.user}, null, 2));
                break;
            case 'goto': {
                const where = rest[0] || '/';
                const origin = await evaluate(cdp, 'location.origin');
                await cdp.send('Page.navigate', {url: /^https?:/.test(where) ? where : origin + (where.startsWith('/') ? '' : '/') + where});
                await new Promise(r => setTimeout(r, 500));
                await waitForLoad(cdp);
                console.log(await evaluate(cdp, 'location.href'));
                break;
            }
            case 'text': {
                const expr = rest[0] ? `(${finder(rest[0])} || {}).innerText` : 'document.body.innerText';
                const text = await evaluate(cdp, expr);
                if (text == null) throw new Error(`Not found: ${rest[0]}`);
                console.log(text.replace(/\n{3,}/g, '\n\n').slice(0, 20000));
                break;
            }
            case 'click':
                await click(cdp, rest[0]);
                console.log(`clicked ${rest[0]}`);
                break;
            case 'type':
                await click(cdp, rest[0]);
                await evaluate(cdp, `(() => { const e = ${finder(rest[0])}; if (e && 'value' in e) { e.select ? e.select() : null; } })()`);
                await cdp.send('Input.dispatchKeyEvent', {type: 'keyDown', key: 'a', code: 'KeyA', windowsVirtualKeyCode: 65, modifiers: 2});
                await cdp.send('Input.dispatchKeyEvent', {type: 'keyUp', key: 'a', code: 'KeyA', windowsVirtualKeyCode: 65, modifiers: 2});
                await key(cdp, 'Backspace');
                await cdp.send('Input.insertText', {text: rest.slice(1).join(' ')});
                console.log(`typed into ${rest[0]}`);
                break;
            case 'key':
                await key(cdp, rest[0]);
                console.log(`pressed ${rest[0]}`);
                break;
            case 'wait':
                await box(cdp, rest[0], Number(rest[1] || 15));
                console.log(`visible: ${rest[0]}`);
                break;
            case 'shot': {
                const file = rest[0] || path.join('target', 'browser-shot.png');
                const {data} = await cdp.send('Page.captureScreenshot', {format: 'png'});
                fs.mkdirSync(path.dirname(path.resolve(file)), {recursive: true});
                fs.writeFileSync(file, Buffer.from(data, 'base64'));
                console.log(path.resolve(file));
                break;
            }
            case 'eval': {
                const expr = rest.join(' ');
                if (/token|password|secret|credential|document\.cookie|getAll\(|localStorage\s*\)|sessionStorage\s*\)|JSON\.stringify\s*\(\s*(local|session)Storage|Object\.(keys|entries|values)\s*\(\s*(local|session)Storage/i.test(expr)) {
                    throw new Error('eval does not read the session (tokens, passwords, stored login, cookies)');
                }
                const value = await evaluate(cdp, expr);
                console.log(typeof value === 'string' ? value : JSON.stringify(value, null, 2));
                break;
            }
            default:
                throw new Error(`Unknown command ${command} - run without arguments for the list`);
        }
    } finally {
        cdp.close();
    }
}

main().catch(e => {
    console.error('❌ ' + e.message);
    process.exit(1);
});
