// Shared by run-group.js and retest.js: run one suite on one env, read its results, name its failures, keep evidence.
const fs = require('fs');
const path = require('path');
const {spawnSync, execSync} = require('child_process');
const {parseResults, formatReport} = require('../jenkins/run-report');

const root = path.resolve(__dirname, '..');
const readJson = file => JSON.parse(fs.readFileSync(file, 'utf8').replace(/^﻿/, ''));
const config = readJson(path.join(__dirname, 'config.json'));

const stamp = () => new Date().toISOString().replace(/[:.]/g, '-').slice(0, 19);
const sleep = ms => Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);
const regex = (source, fallback) => new RegExp(source || fallback, 'i');
// suites that drive the phone: one at a time, and only while Appium's port is free
const needsPhone = suite => regex(config.phoneSuites, '^(mob|hybrid)/').test(suite);
const appiumPort = config.appiumPort || 4723;
// the framework's report folder of an env: Test -> testEnv, Stage -> stageEnv (data.Env.reportFolder)
const envDir = env => env.charAt(0).toLowerCase() + env.slice(1) + 'Env';

/** Arguments like --group smoke --envs Test,Stage --retries 3 -> {group, envs, retries}. */
function args(argv) {
    const out = {};
    for (let i = 0; i < argv.length; i++) {
        if (argv[i].startsWith('--')) {
            const key = argv[i].slice(2);
            const next = argv[i + 1];
            out[key] = next === undefined || next.startsWith('--') ? true : (i++, next);
        }
    }
    return out;
}

/** The suites of a group ("@other" pulls another group in). */
function suitesOf(group) {
    const groups = readJson(path.join(__dirname, 'suite-groups.json'));
    if (!groups[group] || group.startsWith('_')) {
        throw new Error('No suite group "' + group + '" - groups: ' + Object.keys(groups).filter(g => !g.startsWith('_')).join(', '));
    }
    const out = [];
    for (const s of groups[group]) {
        for (const suite of s.startsWith('@') ? suitesOf(s.slice(1)) : [s]) if (!out.includes(suite)) out.push(suite);
    }
    return out;
}

/** Progress of a run, read by wait.js and by whoever asks. */
function writeStatus(dir, status) {
    fs.writeFileSync(path.join(dir, 'status.json'), JSON.stringify({...status, updatedAt: new Date().toISOString()}, null, 2));
}

/** Another bugcycle run still going? (one at a time: one phone, one target/ folder) */
function runningElsewhere(self) {
    const runs = path.join(__dirname, 'runs');
    if (!fs.existsSync(runs)) return null;
    for (const d of fs.readdirSync(runs)) {
        const f = path.join(runs, d, 'status.json');
        if (d === self || !fs.existsSync(f)) continue;
        const s = readJson(f);
        const fresh = Date.now() - Date.parse(s.updatedAt) < 45 * 60 * 1000;
        if (s.state === 'running' && fresh) return d;
    }
    return null;
}

/** Something listens on Appium's port (another mobile run on this machine). */
function phoneBusy() {
    try {
        const out = process.platform === 'win32'
            ? execSync('netstat -ano | findstr :' + appiumPort, {encoding: 'utf8'})
            : execSync('(lsof -nP -iTCP:' + appiumPort + ' -sTCP:LISTEN || ss -ltn "sport = :' + appiumPort + '") 2>/dev/null', {encoding: 'utf8'});
        return process.platform === 'win32' ? /LISTENING/.test(out) : new RegExp('[:.]' + appiumPort + '\\b').test(out);
    } catch (e) {
        return false;                    // the command found nothing
    }
}

/** The phone is taken by another run: wait up to 15 minutes. Returns false if still busy. */
function waitForPhone(log) {
    for (let i = 0; i < 30; i++) {
        if (!phoneBusy()) return true;
        log('phone busy (port ' + appiumPort + ' in use) - waiting 30 s');
        sleep(30000);
    }
    return false;
}

/**
 * One mvn run of {suite} on {env}: log + testng-results copied into {dir} as <name>.log / <name>.xml.
 * Returns {exitCode, ms, result (parseResults) | null, xml, log}.
 */
function runSuite(suite, env, dir, name) {
    const log = path.join(dir, name + '.log');
    const xml = path.join(dir, name + '.xml');
    const results = path.join(root, 'target', 'surefire-reports', 'testng-results.xml');
    if (fs.existsSync(results)) fs.rmSync(results);
    const fd = fs.openSync(log, 'w');
    const started = Date.now();
    const mvn = ['test', '-Dtestng=' + suite, '-DserverType=' + env, '-DbrowserType=' + (config.browserType || 'headlessChrome'),
        ...(config.mvnArgs || [])];
    const [command, commandArgs] = process.platform === 'win32' ? ['cmd.exe', ['/c', 'mvn', ...mvn]] : ['mvn', mvn];
    const r = spawnSync(command, commandArgs, {cwd: root, stdio: ['ignore', fd, fd], windowsHide: true,
        // Maven and the test JVM write their console in UTF-8, or non-ASCII assertion names land in the log as ?????
        env: {...process.env, JAVA_TOOL_OPTIONS: ((process.env.JAVA_TOOL_OPTIONS || '') + ' -Dfile.encoding=UTF-8 -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8').trim()}});
    fs.closeSync(fd);
    let result = null;
    if (fs.existsSync(results)) {
        fs.copyFileSync(results, xml);
        result = parseResults(fs.readFileSync(xml, 'utf8'));
    }
    return {exitCode: r.status, ms: Date.now() - started, result, xml, log};
}

// "an earlier block failed, so there is nothing to work on" - RunContext.get's message, plus the project's own
const KNOCK_ON = regex(config.knockOnPattern, 'Nothing saved as ".*" in this run');
// not the product: the machine or the run's set-up (these are test / env issues, never bugs)
const ENV_ISSUE = regex(config.envIssuePattern, 'MAIL_APP_PASSWORD is not set|EADDRINUSE|instrumentation process is not running'
    + '|Could not start a new session|session not created|Timed out receiving message from renderer');

/**
 * Generated values out (config.volatilePatterns: the run's own user names, stamps ...), then digits, so the same
 * failure has the same key in every attempt.
 */
function normalise(s) {
    let out = String(s);
    for (const v of config.volatilePatterns || []) out = out.replace(new RegExp(v.pattern, v.flags || 'gi'), v.replace || '<x>');
    return out.replace(/\d+/g, '#').replace(/\s+/g, ' ').trim();
}

/**
 * The failures of one block row as keys: one per failed assertion ("#5 Order details · total: expected [...]"
 * - several for a soft group), else the exception and its message. kind = knock-on for the "nothing to work on
 * because an earlier block failed" messages and for skipped blocks.
 */
function failuresOf(row) {
    if (row.status === 'PASS') return [];
    const f = row.failure;
    const at = {n: row.n, method: row.method};
    if (!f) return [{key: row.block + ' | skipped', block: row.block, assertion: '', kind: 'knock-on', why: row.why, ...at}];
    const kind = KNOCK_ON.test(f.message) ? 'knock-on' : ENV_ISSUE.test(f.message) ? 'env' : row.status === 'SKIP' ? 'knock-on' : null;
    // "#5 Order details · total: expected [...]" - one per failed assertion, several in a soft group
    const names = [...f.message.matchAll(/#\d+ ([^#]+?): expected \[/g)].map(m => m[1].trim());
    if (names.length) {
        return names.map(n => ({key: row.block + ' | ' + normalise(n), block: row.block, assertion: n,
            kind: kind || 'assertion', why: row.why, ...at}));
    }
    return [{key: row.block + ' | ' + f.exception + ': ' + normalise(f.message.slice(0, 120)), block: row.block, assertion: '',
        kind: kind || 'error', why: row.why, ...at}];
}

/** Every failure key of a run, plus the blocks that passed (a key whose block passed has passed). */
function verdicts(result) {
    const failures = [];
    const passedBlocks = new Set();
    if (!result) return {failures, passedBlocks, noResult: true};
    for (const row of result.rows) {
        if (row.status === 'PASS') passedBlocks.add(row.block);
        failures.push(...failuresOf(row));
    }
    return {failures, passedBlocks, noResult: false};
}

/** The screenshots + html of the suite's report folder into {dir}/evidence/{name}/ (report/<env>Env/<suite>/). */
function keepEvidence(suite, env, dir, name, sinceMs) {
    const from = path.join(root, 'report', envDir(env), path.basename(suite));
    const to = path.join(dir, 'evidence', name);
    if (!fs.existsSync(from)) return null;
    fs.mkdirSync(to, {recursive: true});
    for (const f of fs.readdirSync(from)) {
        // only what this attempt wrote - the report folder keeps files of older runs with other block names
        if (/\.(png|html)$/i.test(f) && (!sinceMs || fs.statSync(path.join(from, f)).mtimeMs >= sinceMs - 2000)) {
            fs.copyFileSync(path.join(from, f), path.join(to, f));
        }
    }
    return path.relative(root, to).replace(/\\/g, '/');
}

/** The screenshot of block row {n}/{method} inside an evidence folder ("6_verifyThat….png"), or the folder. */
function screenshotOf(evidenceDir, n, method) {
    if (!evidenceDir) return '';
    const full = path.join(root, evidenceDir);
    if (!fs.existsSync(full)) return evidenceDir;
    const png = fs.readdirSync(full).find(f => f === n + '_' + method + '.png') || fs.readdirSync(full).find(f => f.startsWith(n + '_') && f.endsWith('.png'));
    return png ? evidenceDir + '/' + png : evidenceDir;
}

/**
 * Data a run created and did not delete (a run cut short), from its log. config.leftovers.created / .deleted are
 * regexes over the console lines the set-up and clean-up blocks print, group 1 = the item's id or name
 * (e.g. "Created user by API: (\\S+)" / "Deleted user by API: (\\S+)"). Empty = not tracked.
 */
function leftovers(logFile) {
    const rule = config.leftovers || {};
    if (!rule.created || !fs.existsSync(logFile)) return [];
    const text = fs.readFileSync(logFile, 'utf8');
    const created = [...text.matchAll(new RegExp(rule.created, 'g'))].map(m => String(m[1]).toLowerCase());
    const deleted = new Set(rule.deleted ? [...text.matchAll(new RegExp(rule.deleted, 'g'))].map(m => String(m[1]).toLowerCase()) : []);
    return created.filter(u => !deleted.has(u));
}

module.exports = {root, config, readJson, stamp, sleep, args, suitesOf, needsPhone, appiumPort, writeStatus, runningElsewhere,
    waitForPhone, runSuite, failuresOf, verdicts, keepEvidence, screenshotOf, leftovers, normalise, formatReport};
