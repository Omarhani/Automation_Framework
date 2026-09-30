// Runs a suite group, re-runs each failing suite up to `retries` times, and sorts every failure:
//   confirmed  - failed in every attempt (a real, repeatable failure: bug candidate)
//   flaky      - passed in at least one retry (not a bug; worth a look at the test)
//   env        - the machine / run set-up, not the product (mail password, phone port, driver)
//   knock-on   - an earlier block failed, so this one had nothing to work on
//
//   node bugcycle/run-group.js --group smoke [--envs Test,Stage] [--retries 3] [--suites a,b] [--detach]
//
// Writes bugcycle/runs/<time>-<group>/: status.json (progress), summary.json, summary.md, one .log/.xml per attempt,
// evidence/<suite>-<env>-a<n>/ (report screenshots of failing attempts). --detach starts it in the background and
// prints the folder (the tool that starts it may stop after 10 minutes; a group runs longer).
const fs = require('fs');
const path = require('path');
const {spawn} = require('child_process');
const L = require('./lib');

const a = L.args(process.argv.slice(2));
const group = a.group || (a.suites ? 'custom' : null);
if (!group) {
    console.error('usage: node bugcycle/run-group.js --group <name> [--envs Test,Stage] [--retries 3] [--suites a,b] [--detach]');
    process.exit(2);
}
const suites = a.suites ? String(a.suites).split(',').map(s => s.trim()) : L.suitesOf(group);
const envs = a.envs ? String(a.envs).split(',').map(s => s.trim()) : L.config.defaultEnvs;
const retries = a.retries !== undefined ? Number(a.retries) : L.config.retries;
const dir = a.out ? path.resolve(a.out) : path.join(__dirname, 'runs', L.stamp() + '-' + group);
fs.mkdirSync(dir, {recursive: true});

if (a.detach) {
    const rest = process.argv.slice(2).filter(x => x !== '--detach');
    const child = spawn(process.execPath, [__filename, ...rest, '--out', dir], {detached: true, stdio: 'ignore', windowsHide: true});
    child.unref();
    console.log(path.relative(L.root, dir).replace(/\\/g, '/'));
    process.exit(0);
}

const other = L.runningElsewhere(path.basename(dir));
if (other) {
    L.writeStatus(dir, {state: 'refused', reason: 'bugcycle run ' + other + ' is still running'});
    console.error('refused: ' + other + ' is still running');
    process.exit(3);
}

const progress = [];
const log = line => {
    progress.push(new Date().toISOString().slice(11, 19) + ' ' + line);
    fs.appendFileSync(path.join(dir, 'progress.log'), progress[progress.length - 1] + '\n');
};
const total = suites.length * envs.length;
let done = 0;
const summary = {group, suites, envs, retries, startedAt: new Date().toISOString(), runs: []};
const status = extra => L.writeStatus(dir, {state: 'running', group, envs, done, total, ...extra, progress: progress.slice(-12)});

for (const env of envs) {
    for (const suite of suites) {
        const base = suite.replace(/\//g, '_') + '-' + env;
        const entry = {suite, env, attempts: [], findings: [], leftovers: []};
        summary.runs.push(entry);
        if (L.needsPhone(suite) && !L.waitForPhone(log)) {
            entry.blocked = 'phone busy (port ' + L.appiumPort + ') for 15 minutes';
            log(suite + ' on ' + env + ': skipped - ' + entry.blocked);
            done++;
            continue;
        }
        // key -> {failure, failed: n, passed: n, inconclusive: n}
        const keys = new Map();
        for (let attempt = 1; attempt <= 1 + retries; attempt++) {
            const name = base + '-a' + attempt;
            status({current: suite + ' on ' + env + (attempt > 1 ? ' - retry ' + (attempt - 1) + ' of ' + retries : '')});
            log('start ' + suite + ' on ' + env + ' attempt ' + attempt);
            const attemptStart = Date.now();
            const run = L.runSuite(suite, env, dir, name);
            const v = L.verdicts(run.result);
            const c = run.result ? run.result.count : null;
            entry.attempts.push({attempt, ms: run.ms, exitCode: run.exitCode, count: c,
                xml: path.relative(L.root, run.xml).replace(/\\/g, '/'), log: path.relative(L.root, run.log).replace(/\\/g, '/'),
                report: run.result ? L.formatReport(run.result, env + (attempt > 1 ? ' - retry ' + (attempt - 1) : '')) : 'no results (the run did not start - see the log)'});
            entry.leftovers.push(...L.leftovers(run.log));
            log('end ' + suite + ' on ' + env + ' attempt ' + attempt + ': ' + (c ? c.PASS + ' passed, ' + c.FAIL + ' failed, ' + c.SKIP + ' skipped' : 'no results'));

            if (v.failures.length || v.noResult) {
                entry.attempts[entry.attempts.length - 1].evidence = L.keepEvidence(suite, env, dir, name, attemptStart);
            }
            if (attempt === 1) {
                for (const f of v.failures) keys.set(f.key, {failure: f, failed: 0, passed: 0, inconclusive: 0});
                if (v.noResult) keys.set('no results', {failure: {key: 'no results', block: '', assertion: '', kind: 'env',
                    why: 'the run produced no testng-results.xml - see ' + path.basename(run.log)}, failed: 0, passed: 0, inconclusive: 0});
            }
            for (const [key, k] of keys) {
                if (key === 'no results') { v.noResult ? k.failed++ : k.passed++; continue; }
                if (v.failures.some(f => f.key === key)) k.failed++;
                else if (v.passedBlocks.has(k.failure.block)) k.passed++;
                else k.inconclusive++;
            }
            // retry only what can still turn out real: product-looking failures without a pass yet
            // (env and knock-on failures are not retried: a retry cannot make them product bugs)
            const open = [...keys.values()].filter(k => (k.failure.kind === 'assertion' || k.failure.kind === 'error') && k.passed === 0);
            if (open.length === 0) break;
            if (attempt <= retries) log(open.length + ' failure(s) still open - retrying ' + suite + ' on ' + env);
        }
        for (const k of keys.values()) {
            const attempts = k.failed + k.passed + k.inconclusive;
            let verdict = k.failure.kind;
            if (verdict === 'assertion' || verdict === 'error') {
                verdict = k.passed > 0 ? 'flaky' : k.failed === attempts ? 'confirmed' : 'unconfirmed';
            }
            entry.findings.push({...k.failure, verdict, failedIn: k.failed, of: attempts, passedIn: k.passed});
        }
        entry.leftovers = [...new Set(entry.leftovers)];
        done++;
    }
}

summary.finishedAt = new Date().toISOString();
fs.writeFileSync(path.join(dir, 'summary.json'), JSON.stringify(summary, null, 2));
fs.writeFileSync(path.join(dir, 'summary.md'), markdown(summary));
L.writeStatus(dir, {state: 'done', group, envs, done, total, progress: progress.slice(-12),
    summary: path.relative(L.root, path.join(dir, 'summary.md')).replace(/\\/g, '/')});

function markdown(s) {
    const out = [];
    const mins = ms => Math.round(ms / 60000) + ' min';
    const all = s.runs.flatMap(r => r.findings.map(f => ({...f, suite: r.suite, env: r.env})));
    const confirmed = all.filter(f => f.verdict === 'confirmed');
    out.push('# Suite group "' + s.group + '" on ' + s.envs.join(' + ') + ' - ' + s.startedAt.slice(0, 16).replace('T', ' ') + ' UTC');
    out.push('');
    out.push('**Confirmed failures: ' + confirmed.length + '** · flaky: ' + all.filter(f => f.verdict === 'flaky').length
        + ' · env / set-up: ' + all.filter(f => f.verdict === 'env').length + ' · knock-on: ' + all.filter(f => f.verdict === 'knock-on').length
        + ' · retries per failing suite: up to ' + s.retries);
    out.push('');
    out.push('| Suite | Env | First run | Attempts | Time |');
    out.push('|---|---|---|---|---|');
    for (const r of s.runs) {
        const c = r.attempts[0] && r.attempts[0].count;
        out.push('| ' + r.suite + ' | ' + r.env + ' | ' + (r.blocked ? 'blocked: ' + r.blocked : c ? (c.FAIL + c.SKIP === 0 ? '✅ ' : '❌ ') + c.PASS + '/' + (c.PASS + c.FAIL + c.SKIP) : 'no results')
            + ' | ' + r.attempts.length + ' | ' + mins(r.attempts.reduce((t, x) => t + x.ms, 0)) + ' |');
    }
    if (all.length) {
        out.push('');
        out.push('## Failures');
        out.push('');
        out.push('| Verdict | Suite | Env | Block | Assertion / error | Failed in | Evidence |');
        out.push('|---|---|---|---|---|---|---|');
        const order = {confirmed: 0, unconfirmed: 1, flaky: 2, env: 3, 'knock-on': 4};
        for (const f of all.sort((x, y) => order[x.verdict] - order[y.verdict])) {
            const run = s.runs.find(r => r.suite === f.suite && r.env === f.env);
            const ev = L.screenshotOf((run.attempts.find(x => x.evidence) || {}).evidence, f.n, f.method);
            out.push('| ' + f.verdict + ' | ' + f.suite + ' | ' + f.env + ' | ' + f.block + ' | '
                + (f.assertion || f.why).replace(/\|/g, '\\|').replace(/\s+/g, ' ').slice(0, 220) + ' | ' + f.failedIn + '/' + f.of + ' | ' + ev + ' |');
        }
    }
    const left = s.runs.flatMap(r => r.leftovers.map(u => u + ' (' + r.env + ')'));
    if (left.length) {
        out.push('');
        out.push('**Data left behind by a cut-off run**' + ((L.config.leftovers || {}).cleanup ? ' (' + L.config.leftovers.cleanup + ')' : '') + ': ' + left.join(', '));
    }
    for (const r of s.runs) {
        for (const x of r.attempts) {
            out.push('');
            out.push(x.report.replace(/^### /, '### ' + r.suite + ' · '));
        }
    }
    return out.join('\n') + '\n';
}
