// After a retest: moves every bug that passed to the next state of its rule (bugcycle/config.json) with a comment,
// reads it back, and remembers every verdict in bugcycle/runs/watch-state.json (so the watcher does not retest an
// unchanged bug again). A bug that is not fixed / inconclusive is NOT moved - a person decides.
// A bug whose state changed since the retest started (someone moved it) is left alone.
//
//   node bugcycle/move.js --run bugcycle/runs/<time>-retest [--validate]   (--validate: check only, nothing saved)
const fs = require('fs');
const path = require('path');
const L = require('./lib');
const ado = require('./ado');

const statePath = path.join(__dirname, 'runs', 'watch-state.json');

(async () => {
    const a = L.args(process.argv.slice(2));
    if (!a.run) {
        console.error('usage: node bugcycle/move.js --run bugcycle/runs/<folder> [--validate]');
        process.exit(2);
    }
    const dir = path.resolve(a.run);
    const retest = JSON.parse(fs.readFileSync(path.join(dir, 'retest.json'), 'utf8').replace(/^\uFEFF/, ''));
    const seen = fs.existsSync(statePath) ? JSON.parse(fs.readFileSync(statePath, 'utf8').replace(/^\uFEFF/, '')) : {};
    const bugs = await ado.items(retest.results.map(r => r.id));
    const lines = [];
    for (const r of retest.results) {
        const b = bugs.find(x => x.id === r.id);
        if (!b) { lines.push(`#${r.id}: not found in the tracker`); continue; }
        const state = b.fields['System.State'];
        const rule = L.config.retest.find(x => x.states.includes(state) && x.env === r.env);
        const fixed = String(r.verdict).startsWith('fixed');
        let action;
        if (!fixed) {
            action = `${r.verdict} on ${r.env} - left in "${state}" (propose ${L.config.failedRetestProposal}; a person decides)`;
        } else if (!rule) {
            action = `fixed on ${r.env}, but it is now "${state}" (changed during the retest) - not moved`;
        } else {
            const attempts = (r.attempts || []).map(x => x.outcome).join(', ');
            const comment = `<p><b>Retested by automation</b> on ${r.env}, ${new Date().toISOString().slice(0, 16).replace('T', ' ')} UTC: `
                + `suite <code>${r.bug.suite}</code>, block "${r.bug.block}" passed`
                + (r.bug.assertion ? `, assertion <code>${r.bug.assertion}</code> OK` : '')
                + ` (attempts: ${attempts}). Moved ${state} → ${rule.to}. Run: <code>${path.relative(L.root, dir).replace(/\\/g, '/')}</code></p>`;
            await ado.patch(r.id, [
                {op: 'test', path: '/rev', value: b.rev},
                {op: 'add', path: '/fields/System.State', value: rule.to},
                {op: 'add', path: '/fields/System.History', value: comment}
            ], !!a.validate);
            if (a.validate) {
                action = `fixed on ${r.env} - would move "${state}" → "${rule.to}" (validate only, nothing saved)`;
            } else {
                const [after] = await ado.items([r.id]);
                action = `fixed on ${r.env} - moved "${state}" → "${after.fields['System.State']}" (comment added)`;
            }
        }
        if (!a.validate) seen[r.id] = {rev: fixed ? null : b.rev, verdict: r.verdict, env: r.env, at: new Date().toISOString(), run: path.basename(dir)};
        lines.push(`#${r.id}: ${action} | ${ado.link(r.id)}`);
    }
    if (!a.validate) {
        fs.mkdirSync(path.dirname(statePath), {recursive: true});
        fs.writeFileSync(statePath, JSON.stringify(seen, null, 2));
    }
    const text = lines.join('\n');
    fs.writeFileSync(path.join(dir, 'moves.md'), '# Tracker moves\n\n' + lines.map(l => '- ' + l).join('\n') + '\n');
    console.log(text);
})().catch(e => {
    console.error('move failed: ' + e.message);
    process.exit(1);
});
