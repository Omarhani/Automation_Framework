// Retests bugs filed from automation: runs the suite behind each bug (bug-registry.json) on the given env and says
// whether the bug's assertion now passes.
//   fixed      - its block passed (on the first run, or on a retry - then "passed on retry n")
//   not fixed  - the same assertion failed in every attempt (1 + retries)
//   inconclusive - the block never got that far (set-up / knock-on / env) - nothing can be said
//
//   node bugcycle/retest.js --bugs 1201,1207 --env Test [--retries 3] [--detach]
//   node bugcycle/retest.js --plan 1201:Test,1207:Stage              (one env per bug, from its tracker state)
//
// Writes bugcycle/runs/<time>-retest/: status.json, retest.json, retest.md, logs / xml / evidence per attempt.
// It never touches the tracker - moving the bug is the suite-bug-cycle skill's step, after this report.
const fs = require('fs');
const path = require('path');
const {spawn} = require('child_process');
const L = require('./lib');

const a = L.args(process.argv.slice(2));
const registry = JSON.parse(fs.readFileSync(path.join(__dirname, 'bug-registry.json'), 'utf8').replace(/^\uFEFF/, '')).bugs;
let plan = [];
if (a.plan) {
    plan = String(a.plan).split(',').map(p => p.split(':')).map(([id, env]) => ({id: Number(id), env: env || 'Test'}));
} else if (a.bugs) {
    plan = String(a.bugs).split(',').map(id => ({id: Number(id), env: a.env || 'Test'}));
} else {
    console.error('usage: node bugcycle/retest.js --bugs <id,id> --env Test|Stage | --plan <id:env,...> [--retries 3] [--detach]');
    process.exit(2);
}
const retries = a.retries !== undefined ? Number(a.retries) : L.config.retries;
const dir = a.out ? path.resolve(a.out) : path.join(__dirname, 'runs', L.stamp() + '-retest');
fs.mkdirSync(dir, {recursive: true});

if (a.detach) {
    const rest = process.argv.slice(2).filter(x => x !== '--detach');
    spawn(process.execPath, [__filename, ...rest, '--out', dir], {detached: true, stdio: 'ignore', windowsHide: true}).unref();
    console.log(path.relative(L.root, dir).replace(/\\/g, '/'));
    process.exit(0);
}

const other = L.runningElsewhere(path.basename(dir));
if (other) {
    L.writeStatus(dir, {state: 'refused', reason: 'bugcycle run ' + other + ' is still running'});
    process.exit(3);
}

const progress = [];
const log = line => {
    progress.push(new Date().toISOString().slice(11, 19) + ' ' + line);
    fs.appendFileSync(path.join(dir, 'progress.log'), progress[progress.length - 1] + '\n');
};
const results = [];
for (const p of plan) {
    const bug = registry.find(b => b.id === p.id);
    results.push(bug ? {...p, bug, verdict: null, attempts: []}
        : {...p, verdict: 'unknown', why: 'not in bugcycle/bug-registry.json - add its suite, block and assertion'});
}

// one suite run serves every bug of that suite and env
const groups = new Map();
for (const r of results.filter(x => x.bug)) {
    const k = r.bug.suite + '|' + r.env;
    if (!groups.has(k)) groups.set(k, []);
    groups.get(k).push(r);
}
let done = 0;
for (const [k, bugs] of groups) {
    const [suite, env] = k.split('|');
    if (L.needsPhone(suite) && !L.waitForPhone(log)) {
        for (const r of bugs) { r.verdict = 'inconclusive'; r.why = 'phone busy (port ' + L.appiumPort + ') for 15 minutes'; }
        continue;
    }
    for (let attempt = 1; attempt <= 1 + retries; attempt++) {
        const open = bugs.filter(r => !r.verdict);
        if (open.length === 0) break;
        const name = suite.replace(/\//g, '_') + '-' + env + '-a' + attempt;
        L.writeStatus(dir, {state: 'running', done, total: groups.size, current: suite + ' on ' + env + ' attempt ' + attempt,
            bugs: open.map(r => r.id), progress: progress.slice(-12)});
        log('start ' + suite + ' on ' + env + ' attempt ' + attempt + ' for bug(s) ' + open.map(r => r.id).join(', '));
        const attemptStart = Date.now();
        const run = L.runSuite(suite, env, dir, name);
        const v = L.verdicts(run.result);
        const evidence = v.failures.length || v.noResult ? L.keepEvidence(suite, env, dir, name, attemptStart) : null;
        for (const r of open) {
            const target = L.normalise(r.bug.assertion);
            const mine = v.failures.find(f => f.block === r.bug.block && (!target || L.normalise(f.assertion) === target));
            const blockFailure = v.failures.find(f => f.block === r.bug.block);
            let outcome;
            if (v.passedBlocks.has(r.bug.block)) outcome = 'passed';
            else if (mine && (mine.kind === 'assertion' || mine.kind === 'error')) outcome = 'failed';
            else outcome = 'inconclusive';
            r.attempts.push({attempt, outcome, why: outcome === 'passed' ? '' : (blockFailure ? blockFailure.why : v.noResult ? 'no results' : 'block did not run'),
                xml: path.relative(L.root, run.xml).replace(/\\/g, '/'), evidence});
            if (outcome === 'passed') r.verdict = attempt === 1 ? 'fixed' : 'fixed (passed on retry ' + (attempt - 1) + ')';
        }
        log('end ' + suite + ' on ' + env + ' attempt ' + attempt);
        L.leftovers(run.log).forEach(u => log('LEFT BEHIND: ' + u + ' (' + env + ')'));
    }
    for (const r of bugs.filter(x => !x.verdict)) {
        const failed = r.attempts.filter(x => x.outcome === 'failed').length;
        r.verdict = failed === r.attempts.length ? 'not fixed' : 'inconclusive';
        r.why = r.attempts[r.attempts.length - 1].why;
    }
    done++;
}

fs.writeFileSync(path.join(dir, 'retest.json'), JSON.stringify({finishedAt: new Date().toISOString(), retries, results}, null, 2));
const md = ['# Retest - ' + new Date().toISOString().slice(0, 16).replace('T', ' ') + ' UTC', '',
    '| Bug | Env | Suite / block | Verdict | Attempts | Why (last failure) |', '|---|---|---|---|---|---|'];
for (const r of results) {
    md.push('| ' + r.id + ' | ' + r.env + ' | ' + (r.bug ? r.bug.suite + ' · ' + r.bug.block : '-') + ' | ' + r.verdict + ' | '
        + (r.attempts ? r.attempts.map(x => x.outcome).join(', ') : '-') + ' | ' + String(r.why || '').replace(/\|/g, '\\|').replace(/\s+/g, ' ').slice(0, 200) + ' |');
}
fs.writeFileSync(path.join(dir, 'retest.md'), md.join('\n') + '\n');
L.writeStatus(dir, {state: 'done', done, total: groups.size, progress: progress.slice(-12),
    summary: path.relative(L.root, path.join(dir, 'retest.md')).replace(/\\/g, '/')});
