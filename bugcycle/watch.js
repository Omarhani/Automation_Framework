// Watcher: which automation bugs are waiting for a retest? Asks the tracker for bugs carrying the automation tag that
// are in a retest state of bugcycle/config.json ("retest": the states that mean "the fix is on <env>").
// A bug already retested at its current revision (not fixed / inconclusive) is left alone until the dev team changes
// it again - so the watcher never loops on the same failure. Memory: bugcycle/runs/watch-state.json.
//
//   node bugcycle/watch.js            -> prints "PLAN: 1201:Test,..." (or "PLAN: -") and one line per bug
//   node bugcycle/watch.js --json     -> the same as JSON
const fs = require('fs');
const path = require('path');
const L = require('./lib');
const ado = require('./ado');

const statePath = path.join(__dirname, 'runs', 'watch-state.json');
const readState = () => fs.existsSync(statePath) ? JSON.parse(fs.readFileSync(statePath, 'utf8').replace(/^\uFEFF/, '')) : {};

(async () => {
    const a = L.args(process.argv.slice(2));
    const rules = L.config.retest;
    const states = rules.flatMap(r => r.states);
    const ids = await ado.wiql(`SELECT [System.Id] FROM WorkItems WHERE [System.TeamProject]='${L.config.tracker.project}' `
        + `AND [System.WorkItemType]='${L.config.tracker.bugType || 'Bug'}' AND [System.Tags] CONTAINS '${L.config.tracker.tags}' `
        + `AND [System.State] IN (${states.map(s => `'${s}'`).join(',')})`);
    const bugs = await ado.items(ids);
    const registry = JSON.parse(fs.readFileSync(path.join(__dirname, 'bug-registry.json'), 'utf8').replace(/^\uFEFF/, '')).bugs;
    const seen = readState();
    const out = [];
    for (const b of bugs) {
        const state = b.fields['System.State'];
        const rule = rules.find(r => r.states.includes(state));
        const rev = b.rev;
        const last = seen[b.id];
        let decision = 'retest';
        if (!registry.some(r => r.id === b.id)) decision = 'skip: not in bugcycle/bug-registry.json (no suite / block to run)';
        else if (last && last.rev === rev && !String(last.verdict).startsWith('fixed')) decision = 'skip: already retested at this revision (' + last.verdict + ') - waiting for the dev team';
        out.push({id: b.id, title: b.fields['System.Title'], state, env: rule.env, to: rule.to, rev, decision,
            assignedTo: (b.fields['System.AssignedTo'] || {}).displayName || '', link: ado.link(b.id)});
    }
    const plan = out.filter(x => x.decision === 'retest').map(x => x.id + ':' + x.env).join(',');
    if (a.json) {
        console.log(JSON.stringify({plan, bugs: out}, null, 2));
        return;
    }
    console.log('PLAN: ' + (plan || '-'));
    for (const x of out) console.log(`#${x.id} [${x.state} -> retest on ${x.env}] ${x.decision} | ${x.title.slice(0, 90)}`);
    if (!out.length) console.log('No automation bug is waiting for a retest (' + states.join(' / ') + ').');
})().catch(e => {
    console.error('watch failed: ' + e.message);
    process.exit(1);
});
