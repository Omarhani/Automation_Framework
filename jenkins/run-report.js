// Run report: one row per <test> block of the last run - passed / failed / skipped, and why it failed.
// Reads target/surefire-reports/testng-results.xml (written by every mvn test run).
//
//   node jenkins/run-report.js [env label] [path to testng-results.xml]
//
// A failure shows the exception message plus the first line of project code in its stack trace, so a bare
// "expected [3] but found [2]" says which check it was (OrderTests.java:84 getItemCount).
//
// Also a module: require('./run-report').parseResults(xml) gives the same rows as objects (bugcycle/run-group.js).
const fs = require('fs');
const path = require('path');

const root = path.resolve(__dirname, '..');

const decode = s => s.replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"')
    .replace(/&apos;/g, "'").replace(/&amp;/g, '&');
const attrs = tag => {
    const out = {};
    for (const m of tag.matchAll(/([\w-]+)="([^"]*)"/g)) out[m[1]] = decode(m[2]);
    return out;
};
const cdata = (body, element) => {
    const m = body.match(new RegExp('<' + element + '>\\s*<!\\[CDATA\\[([\\s\\S]*?)\\]\\]>'));
    return m ? m[1] : '';
};
const seconds = ms => Math.round(Number(ms || 0) / 1000) + ' s';
const cell = s => s.replace(/\|/g, '\\|').replace(/\s+/g, ' ').trim();

function sourceLine(cls, line) {
    const rel = cls.replace(/\./g, '/') + '.java';
    for (const dir of ['src/test/java', 'src/main/java']) {
        const src = path.join(root, dir, rel);
        if (fs.existsSync(src)) return (fs.readFileSync(src, 'utf8').split(/\r?\n/)[line - 1] || '').trim().slice(0, 110);
    }
    return '';
}

// Where it failed in our code: the line of the test method (which step / which check), plus the
// helper it failed inside when that is a different file (MethodHandlesWeb.myAssertEquals, a page object...)
function whereInOurCode(stack, testClass) {
    // the assertion engine itself is never "where": its message already names the check (#6 Order details · total)
    const frames = [...stack.matchAll(/at ((?:apis|api|base|mob|ui|tests|utils|pages|screens|data|reader)\.[\w.$]+)\.([\w$<>]+)\((\w+\.java):(\d+)\)/g)]
        .filter(m => m[1] !== 'utils.ReportedAssertions' && !(m[1] === 'utils.MethodHandlesWeb' && /^(myAssert|softly)/.test(m[2])))
        .map(m => ({cls: m[1].split('$')[0], file: m[3], line: Number(m[4])}));
    if (frames.length === 0) return '';
    const test = frames.find(f => f.cls === testClass);
    const top = frames[0];
    const main = test || top;
    const code = sourceLine(main.cls, main.line);
    let where = main.file + ':' + main.line + (code ? ' `' + code + '`' : '');
    if (test && top.cls !== test.cls) where += ' (in ' + top.file + ':' + top.line + ')';
    return where;
}

/** {exception: short class name, message: first line, where} of a failed method's body, or null. */
function failureOf(body, testClass) {
    const ex = body.match(/<exception class="([^"]*)"/);
    if (!ex) return null;
    return {
        exception: ex[1].split('.').pop(),
        message: (cdata(body, 'message').split(/\r?\n/).find(l => l.trim()) || '').trim(),
        where: whereInOurCode(cdata(body, 'full-stacktrace'), testClass)
    };
}

function reason(body, testClass) {
    const f = failureOf(body, testClass);
    if (!f) return '';
    return (f.exception === 'AssertionError' ? '' : f.exception + ': ') + f.message.slice(0, 400) + (f.where ? ' - at ' + f.where : '');
}

// "…[pri:5, instance:ui.orders.OrderTests@4f2b503c]" -> ui.orders.OrderTests
const testClassOf = m => ((m.a.signature || '').match(/instance:([\w.$]+)@/) || [])[1] || '';

/**
 * The run as data: {suite: {name, started-at, finished-at, duration-ms}, count: {PASS, FAIL, SKIP}, rows: [{n, block,
 * method, cls, status, ms, why, failure: {exception, message, where} | null}]} - one row per test method, a block where
 * nothing ran is one SKIP row with the set-up's failure.
 */
function parseResults(xml) {
    const suite = attrs((xml.match(/<suite [^>]*>/) || [''])[0]);
    const rows = [];
    const count = {PASS: 0, FAIL: 0, SKIP: 0};
    let n = 0;
    for (const t of xml.matchAll(/<test ([^>]*)>([\s\S]*?)<\/test>/g)) {
        const block = attrs(t[1]);
        n++;
        const methods = [...t[2].matchAll(/<test-method ([^>]*?)(?:\/>|>([\s\S]*?)<\/test-method>)/g)]
            .map(m => ({a: attrs(m[1]), body: m[2] || ''}));
        const configFail = methods.find(m => m.a['is-config'] === 'true' && m.a.status === 'FAIL');
        const tests = methods.filter(m => m.a['is-config'] !== 'true');
        if (tests.length === 0) {
            // nothing ran: a set-up (driver, phone, app) failed or the block was skipped with it
            const why = configFail ? 'set-up ' + configFail.a.name + ' failed: ' + reason(configFail.body, testClassOf(configFail)) : 'no test ran';
            rows.push({n, block: block.name, method: '', cls: '', status: 'SKIP', ms: Number(block['duration-ms'] || 0), why,
                failure: configFail ? failureOf(configFail.body, testClassOf(configFail)) : null});
            count.SKIP++;
            continue;
        }
        for (const m of tests) {
            const status = m.a.status;
            count[status] = (count[status] || 0) + 1;
            let why = status === 'PASS' ? '' : reason(m.body, testClassOf(m));
            if (status === 'SKIP' && !why) why = configFail ? 'set-up ' + configFail.a.name + ' failed: ' + reason(configFail.body, testClassOf(configFail)) : 'skipped';
            rows.push({n, block: block.name, method: m.a.name, cls: testClassOf(m), status, ms: Number(m.a['duration-ms'] || 0), why,
                failure: status === 'PASS' ? null : failureOf(m.body, testClassOf(m))});
        }
    }
    return {suite, count, rows};
}

/** The markdown report (what the CLI prints). */
function formatReport(result, label) {
    const {suite, count, rows} = result;
    const total = count.PASS + count.FAIL + count.SKIP;
    const pct = k => total ? Math.round(100 * count[k] / total) + '%' : '0%';
    const icon = {PASS: 'PASS', FAIL: '**FAIL**', SKIP: '*SKIP*'};
    const out = [];
    out.push('### ' + (label ? label + ' - ' : '') + (suite.name || 'suite'));
    out.push('');
    out.push('Passed **' + count.PASS + '** (' + pct('PASS') + ') · Failed **' + count.FAIL + '** (' + pct('FAIL') + ') · Skipped **'
        + count.SKIP + '** (' + pct('SKIP') + ') of ' + total + ' · ' + seconds(suite['duration-ms'])
        + ' · ' + (suite['started-at'] || '') + ' → ' + (suite['finished-at'] || ''));
    out.push('');
    out.push('| # | Block | Result | Time | Why it failed |');
    out.push('|---|---|---|---|---|');
    for (const r of rows) {
        out.push('| ' + r.n + ' | ' + cell(r.block) + ' | ' + icon[r.status] + ' | ' + seconds(r.ms) + ' | ' + cell(r.why) + ' |');
    }
    return out.join('\n');
}

module.exports = {parseResults, formatReport};

if (require.main === module) {
    const label = process.argv[2] || '';
    const file = process.argv[3] || path.join(root, 'target', 'surefire-reports', 'testng-results.xml');
    console.log(formatReport(parseResults(fs.readFileSync(file, 'utf8')), label));
}
