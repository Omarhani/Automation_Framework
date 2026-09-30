// Waits for a detached bugcycle run: returns when it is done, or after <seconds> (default 540 - under the 10-minute
// tool limit) with its progress. Call it again until it prints "state: done".
//
//   node bugcycle/wait.js bugcycle/runs/<folder> [seconds]
const fs = require('fs');
const path = require('path');
const {sleep} = require('./lib');

const dir = path.resolve(process.argv[2] || '');
const limit = Number(process.argv[3] || 540) * 1000;
const file = path.join(dir, 'status.json');
const started = Date.now();
let s = null;
while (Date.now() - started < limit) {
    if (fs.existsSync(file)) {
        s = JSON.parse(fs.readFileSync(file, 'utf8'));
        if (s.state !== 'running') break;
    }
    sleep(15000);
}
if (!s) {
    console.log('state: not started (no status.json in ' + dir + ')');
    process.exit(1);
}
console.log('state: ' + s.state + (s.current ? ' - ' + s.current : '') + ' (' + (s.done || 0) + '/' + (s.total || '?') + ')'
    + (s.reason ? ' - ' + s.reason : ''));
for (const p of s.progress || []) console.log('  ' + p);
if (s.summary) console.log('summary: ' + s.summary);
