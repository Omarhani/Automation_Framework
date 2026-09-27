const fs = require('fs');
const path = require('path');

// ---------------------------------------------------------------------------------------------------------------
// AI test run -> Extent-style HTML report (green "AI Report" theme).
// Copy this file to the scratchpad, fill in the settings, LOGS and CASES below, then: node build-report.example.js
// Output: <REPORT_DIR>/<ENV>/<REPORT_NAME>/<REPORT_NAME>.html - put the run's .md, .gif and .jpg in the same folder.
// ---------------------------------------------------------------------------------------------------------------
const ENV = 'testEnv';                                   // report folder of the env you tested: testEnv, stageEnv ...
const REPORT_NAME = 'Add-User-And-Assign-Role_2026-01-31'; // <Suite-Title>_<yyyy-mm-dd>
const REPORT_DIR = path.resolve('report/ai-runs');       // run from the project root, or give an absolute path
const SUITE = 'Add User And Assign Role';                // shown in the title and the green badge
const RUN_DATE = new Date(2026, 0, 31);                  // the day the run happened (month is 0-based)
const ENVIRONMENT_LINE = 'https://test.example.com &middot; Chrome (profile: QA) &middot; qa.admin (Admin) &middot; EN';
const DEVICE = 'Chrome - QA profile (Admin)';

const dir = path.join(REPORT_DIR, ENV, REPORT_NAME);
fs.mkdirSync(dir, { recursive: true });
const MONTHS = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
const pad = n => String(n).padStart(2, '0');
const DATE_ISO = `${RUN_DATE.getFullYear()}-${pad(RUN_DATE.getMonth() + 1)}-${pad(RUN_DATE.getDate())}`;
const DATE_DOTS = `${pad(RUN_DATE.getMonth() + 1)}.${pad(RUN_DATE.getDate())}.${RUN_DATE.getFullYear()}`;
const DATE_LONG = `${MONTHS[RUN_DATE.getMonth()]} ${pad(RUN_DATE.getDate())}, ${RUN_DATE.getFullYear()}`;

// The shell (same as ai-report-template-head.html in this skill folder).
const tpl = `

<!DOCTYPE html>
<html>
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
<title>AI Report</title>
<link rel="shortcut icon" href="https://cdn.jsdelivr.net/gh/extent-framework/extent-github-cdn@b00a2d0486596e73dd7326beacf352c639623a0e/commons/img/logo.png">
<link href="https://cdn.jsdelivr.net/gh/extent-framework/extent-github-cdn@ce8b10435bcbae260c334c0d0c6b61d2c19b6168/spark/css/spark-style.css" rel="stylesheet" />
<link href="https://stackpath.bootstrapcdn.com/font-awesome/4.7.0/css/font-awesome.min.css" rel="stylesheet">
<script src="https://cdn.jsdelivr.net/gh/extent-framework/extent-github-cdn@7cc78ce/spark/js/jsontree.js"></script>
<style type="text/css">
.details-col{direction:auto;unicode-bidi:plaintext}
/* AI Report theme: green instead of Spark's dark blue */
body.dark,.dark .header{background-color:#0b2e1f !important;color:#e6f4ea}
.dark .side-nav,.dark .test-list{background-color:#134e36 !important}
.dark .test-wrapper,.dark .test-content,.dark .dashboard-view,.dark .dashboard-view .container-fluid{background-color:#1a5c40 !important}
.dark .test-item.active,.dark .test-item:hover{background-color:#0f3d2a !important}
.dark .test-list-tools,.dark .header{border-bottom:1px solid #2f7a55 !important}
.dark .test-item,.dark .table>thead>tr>th,.dark .table>tbody>tr>td{border-bottom:1px solid #2f7a55 !important}
.dark .side-nav .side-nav-inner .side-nav-menu{border-right:1px solid #2f7a55 !important}
.dark .card{border:1px solid #2f7a55 !important}
.dark .search-input input{background-color:#134e36;color:#e6f4ea;border-color:#2f7a55}
/* Text logo */
.header .vheader .nav-logo{width:auto;padding:0 18px}
.header .vheader .nav-logo>a .logo.ai-logo{background:none;width:auto;min-height:0;line-height:64px;font-size:20px;font-weight:700;letter-spacing:.5px;color:#fff;white-space:nowrap}
.header .vheader .nav-logo>a .logo.ai-logo i{color:#4ade80;margin-right:6px}
@media only screen and (max-width:992px){.header .vheader .nav-logo{width:auto;padding:0 12px}.header .vheader .nav-logo>a .logo.ai-logo{line-height:52px;font-size:16px}}
.suite-title{font-size:14px;padding:6px 12px;background-color:#16a34a !important}
</style></head><body class="spa -report dark">
  <div class="app">
    <div class="layout">
<div class="header navbar">
<div class="vheader">
<div class="nav-logo">
<a href="#">
<div class="logo ai-logo"><i class="fa fa-magic"></i>AI Report</div>
</a>
</div>
<ul class="nav-left">
<li class="search-box">
<a class="search-toggle" href="#">
<i class="search-icon fa fa-search"></i>
<i class="search-icon-close fa fa-close"></i>
</a>
</li>
<li class="search-input"><input id="search-tests" class="form-control" type="text" placeholder="Search..."></li>
</ul>
<ul class="nav-right">
<li class="m-r-10">
<a href="#"><span class="badge badge-primary suite-title">SUITE</span></a>
</li>
<li class="m-r-10">
<a href="#"><span class="badge badge-primary">REPORT_DATE</span></a>
</li>
</ul>
</div>
</div><div class="side-nav">
<div class="side-nav-inner">
<ul class="side-nav-menu">
<li class="nav-item dropdown" onclick="toggleView('test-view')">
<a id="nav-test" class="dropdown-toggle" href="#">
<span class="ico"><i class="fa fa-list"></i></span>
</a>
</li>
<li class="nav-item dropdown" onclick="toggleView('dashboard-view')">
<a id="nav-dashboard" class="dropdown-toggle" href="#">
<span class="ico"><i class="fa fa-bar-chart"></i></span>
</a>
</li>
</ul>
</div>
</div>      <div class="vcontainer">
        <div class="main-content">
<div class="test-wrapper row view test-view">
  <div class="test-list">
    <div class="test-list-tools">
<ul class="tools pull-left">
<li><a href="#"><span class="font-size-14">Tests</span></a></li>
</ul>
<ul class="tools text-right">
<li class="dropdown">
<a href="#" class="dropdown-toggle" data-toggle="dropdown"><i class="fa fa-exclamation-circle"></i></a>
<ul id="status-toggle" class="dropdown-menu dropdown-md p-v-0">
<a class="dropdown-item" status="pass" href="#"><span>Pass</span><span class="status success"></span></a>
<div class="dropdown-divider"></div>
<a status="clear" class="dropdown-item" href="#"><span>Clear</span><span class="pull-right"><i class="fa fa-close"></i></span></a>
</ul>
</li>
</ul>
</div>    <div class="test-list-wrapper scrollable">
      <ul class="test-list-item">`;

const esc = s => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
const cls = { Info: 'info-bg', Pass: 'pass-bg', Fail: 'fail-bg', Warning: 'warning-bg', Skip: 'skip-bg' };

// Step logs exactly as captured by __log / __logDump() (time|ms|status|step), one entry per case key.
// The last line of each log is the case's result line. "<what> actual: x / expected: y" steps become
// Actual / Expected banner pairs.
const LOGS = {
SETUP: `10:02:00 AM|1769853720000|Info|SETUP remove leftover user qa.user1 - start
10:02:03 AM|1769853723000|Info|Insert "qa.user1" in Search field
10:02:05 AM|1769853725000|Info|Press Enter
10:02:07 AM|1769853727000|Pass|Verify rows for qa.user1 actual: 0 / expected: 0
10:02:07 AM|1769853727000|Pass|SETUP end - PASS (nothing to remove)`,
TC01: `10:03:10 AM|1769853790000|Info|TC-01 addNewUserAndVerifyInfo - start
10:03:10 AM|1769853790100|Info|Navigate to Users page
10:03:14 AM|1769853794000|Info|Click Add User button
10:03:18 AM|1769853798000|Info|Insert "QA" in First Name field
10:03:21 AM|1769853801000|Info|Insert "User" in Last Name field
10:03:25 AM|1769853805000|Info|Insert "qa.user1" in User Name field
10:03:30 AM|1769853810000|Info|Select "Sales" from Department dropdown
10:03:34 AM|1769853814000|Info|Click Save button
10:03:36 AM|1769853816000|Pass|Verify toast message actual: User added successfully / expected: User added successfully
10:03:45 AM|1769853825000|Info|Insert "qa.user1" in Search field
10:03:47 AM|1769853827000|Pass|Verify User Name column actual: qa.user1 / expected: qa.user1
10:03:47 AM|1769853827000|Pass|Verify Department column actual: Sales / expected: Sales
10:03:47 AM|1769853827000|Pass|TC-01 end - PASS`,
TC02: `10:05:00 AM|1769853900000|Info|TC-02 assignRoleToUser - start
10:05:02 AM|1769853902000|Info|Click Edit row action for qa.user1
10:05:05 AM|1769853905000|Fail|Verify Roles tab actual: not shown / expected: shown
10:05:20 AM|1769853920000|Info|Click Roles tab
10:05:24 AM|1769853924000|Info|Check Reviewer checkbox
10:05:27 AM|1769853927000|Info|Click Save button
10:05:29 AM|1769853929000|Pass|Verify toast message actual: User updated successfully / expected: User updated successfully
10:05:29 AM|1769853929000|Pass|TC-02 end - PASS (FLAKY: 1st check ran before the dialog finished loading)`,
CLEANUP: `10:07:00 AM|1769854020000|Info|CLEANUP delete user qa.user1 - start
10:07:02 AM|1769854022000|Info|Insert "qa.user1" in Search field
10:07:05 AM|1769854025000|Info|Click Delete row action
10:07:08 AM|1769854028000|Info|Click OK button in confirmation dialog
10:07:11 AM|1769854031000|Pass|Verify rows for qa.user1 actual: 0 / expected: 0
10:07:11 AM|1769854031000|Pass|CLEANUP end - PASS`,
};

// Re-label a mis-logged entry by its ms (explain it in Observations; never edit the raw log above).
const relabel = e => {
  if (e.ms === 1769853905000) return { ...e, status: 'Warning', step: 'RETRACTED - ' + e.step + ' (dialog had not finished loading; passed on retry)' };
  return e;
};

const parse = s => s.split('\n').map(l => { const [t, ms, status, ...rest] = l.split('|'); return { t, ms: +ms, status, step: rest.join('|') }; });

const row = (t, st, html) => `      <tr class="event-row">
        <td><span class="badge log ${cls[st]}">${st}</span></td>
        <td>${t}</td>
        <td>
          ${html}
        </td>
      </tr>`;
const banner = (t, text, color) => row(t, 'Info', `<span class='badge white-text ${color}'>------------------- ${text} -------------------</span>`);

function rowsFor(log, extra) {
  const out = [];
  const first = log[0], last = log[log.length - 1];
  out.push(banner(first.t, 'Environment', 'teal'));
  out.push(row(first.t, 'Info', ENVIRONMENT_LINE));
  out.push(banner(first.t, 'Steps To Reproduce', 'teal'));
  for (const e of log.slice(0, -1)) {
    const m = e.step.match(/^(.+?) actual: (.*) \/ expected: (.*)$/);
    if (m && !e.step.startsWith('RETRACTED')) {
      out.push(row(e.t, 'Info', esc(m[1])));
      out.push(banner(e.t, 'Actual Result', 'brown'));
      out.push(row(e.t, e.status, esc(m[2])));
      out.push(banner(e.t, 'Expected Result', 'brown'));
      out.push(row(e.t, 'Info', esc(m[3])));
    } else {
      out.push(row(e.t, e.status, esc(e.step)));
    }
  }
  out.push(banner(last.t, 'Ends of Steps', 'teal'));
  out.push(row(last.t, last.status, esc(last.step)));
  for (const x of extra) out.push(row(last.t, 'Info', x));
  return out;
}

const dur = (a, b) => { const ms = b - a; const h = Math.floor(ms / 3600000), m = Math.floor(ms / 60000) % 60, s = Math.floor(ms / 1000) % 60, r = ms % 1000; const p = (n, w = 2) => String(n).padStart(w, '0'); return `${p(h)}:${p(m)}:${p(s)}:${p(r, 3)}`; };

// One entry per case, in run order. media = the case's .gif / .jpg in the report folder (null = none).
const CASES = [
  { id: 1, key: 'SETUP', tag: 'SETUP', name: 'Setup - remove leftover user qa.user1 -', status: 'pass', media: null,
    extra: ['Not recorded: setup ran before the first test case.'] },
  { id: 2, key: 'TC01', tag: 'TC-01', name: 'addNewUserAndVerifyInfo', status: 'pass', media: 'addNewUserAndVerifyInfo.gif',
    extra: [`<a href='addNewUserAndVerifyInfo.gif'> Download Video </a>`] },
  { id: 3, key: 'TC02', tag: 'TC-02', name: 'assignRoleToUser', status: 'pass', media: 'assignRoleToUser.gif',
    extra: [`<a href='assignRoleToUser.gif'> Download Video </a>`, 'FLAKY: the first check ran while the edit dialog was still loading; passed on retry.'] },
  { id: 4, key: 'CLEANUP', tag: 'CLEANUP', name: 'Clean up - delete user qa.user1 -', status: 'pass', media: 'cleanupDeleteUser.gif',
    extra: [`<a href='cleanupDeleteUser.gif'> Download Video </a>`] },
];

const item = (c, log, rows) => {
  const first = log[0], last = log[log.length - 1];
  const d = dur(first.ms, last.ms);
  const label = c.status[0].toUpperCase() + c.status.slice(1);
  return `        <li class="test-item"  status="${c.status}" test-id="${c.id}"
          author="AI (Claude)"
          tag="${c.tag}"
          device="${esc(DEVICE)}">
          <div class="test-detail">
            <p class="name">${esc(c.name)}</p>
            <p class="text-sm">
              <span>${first.t}</span> / <span>${d}</span>
              <span class="badge ${c.status}-bg log float-right">${label}</span>
            </p>
          </div>
          <div class="test-contents d-none">
<div class="detail-head">
<div class="p-v-10">
<div class="info">
<h5 class="test-status text-${c.status}">${esc(c.name)}</h5>
<span class='badge badge-success'>${DATE_DOTS} ${first.t}</span>
<span class='badge badge-danger'>${DATE_DOTS} ${last.t}</span>
<span class='badge badge-default'>${d}</span>
&middot; <span class='uri-anchor badge badge-default'>#test-id=${c.id}</span>
<span title='Skip to the next failed step' class='badge badge-danger pointer float-right ne ml-1'><i class="fa fa-fast-forward"></i></span>
<span title='Collapse all nodes' class='badge badge-default pointer float-right ct ml-1'><i class="fa fa-compress"></i></span>
<span title='Expand all nodes' class='badge badge-default pointer float-right et'><i class="fa fa-expand"></i></span>
</div>
</div>
</div>${c.media ? `    <div class="row mb-3"><div class="col-md-3">
<img data-featherlight='${c.media}' src="${c.media}">
    </div></div>` : ''}
<div class="detail-body mt-4">
<table class="table table-sm">
  <thead><tr><th class="status-col">Status</th><th class="timestamp-col">Timestamp</th><th class="details-col">Details</th></tr></thead>
  <tbody>
${rows.join('\n')}
  </tbody>
</table>
</div>
          </div>
        </li>`;
};

const built = CASES.map(c => { const log = parse(LOGS[c.key]).map(relabel); const rows = rowsFor(log, c.extra); return { c, log, rows }; });
const allRows = built.flatMap(b => b.rows);
const cnt = k => allRows.filter(r => r.includes(`log ${k}`)).length;
const ev = { total: allRows.length, pass: cnt('pass-bg'), fail: cnt('fail-bg'), warn: cnt('warning-bg'), skip: cnt('skip-bg'), info: cnt('info-bg') };
const tests = { pass: CASES.filter(c => c.status === 'pass').length, fail: CASES.filter(c => c.status === 'fail').length, skip: CASES.filter(c => c.status === 'skip').length };
const start = built[0].log[0], end = built[built.length - 1].log.slice(-1)[0];
const fmtBig = t => DATE_LONG + ' ' + t.replace(/^(\d):/, '0$1:');

let head = tpl.slice(0, tpl.indexOf('      <ul class="test-list-item">'));
head = head
  .replace(/<title>.*<\/title>/, `<title>AI Report | ${esc(SUITE)} | ${DATE_ISO}</title>`)
  .replace(/<span class="badge badge-primary suite-title">.*?<\/span>/, `<span class="badge badge-primary suite-title">${esc(SUITE)}</span>`)
  .replace('REPORT_DATE', fmtBig(start.t))
  .replace('<a class="dropdown-item" status="pass" href="#"><span>Pass</span><span class="status success"></span></a>',
    '<a class="dropdown-item" status="pass" href="#"><span>Pass</span><span class="status success"></span></a>\n<a class="dropdown-item" status="skip" href="#"><span>Skip</span><span class="status skip"></span></a>');

const timeline = built.map(b => `"${b.c.name}":${((b.log.slice(-1)[0].ms - b.log[0].ms) / 1000).toFixed(2)}`).join(',');
const body = `      <ul class="test-list-item">
${built.map(b => item(b.c, b.log, b.rows)).join('\n')}
      </ul>
    </div>
  </div>
<div class="test-content scrollable">
<div class="test-content-tools">
<ul><li><a class="back-to-test" href="#"><i class="fa fa-arrow-left"></i></a></li></ul>
</div>
<div class="test-content-detail"><div class="detail-body"></div></div>
</div></div>
<div class="container-fluid p-4 view dashboard-view">
<div class="row">
<div class="col-md-3"><div class="card"><div class="card-body"><p class="m-b-0">Started</p><h3>${fmtBig(start.t)}</h3></div></div></div>
<div class="col-md-3"><div class="card"><div class="card-body"><p class="m-b-0">Ended</p><h3>${fmtBig(end.t)}</h3></div></div></div>
<div class="col-md-3"><div class="card"><div class="card-body"><p class="m-b-0 text-pass">Tests Passed</p><h3>${tests.pass}</h3></div></div></div>
<div class="col-md-3"><div class="card"><div class="card-body"><p class="m-b-0 text-fail">Tests Failed</p><h3>${tests.fail}</h3></div></div></div>
</div>
<div class="row">
<div class="col-md-6"><div class="card"><div class="card-header"><h6 class="card-title">Tests</h6></div>
<div class="card-body"><div class=""><canvas id='parent-analysis' width='115' height='90'></canvas></div></div>
<div class="card-footer"><div><small><b>${tests.pass}</b> tests passed</small></div>
<div><small><b>${tests.fail}</b> tests failed, <b>${tests.skip}</b> skipped, <b>0</b> others</small></div></div>
</div></div>
<div class="col-md-6"><div class="card"><div class="card-header"><h6 class="card-title">Log events</h6></div>
<div class="card-body"><div class=""><canvas id='events-analysis' width='115' height='90'></canvas></div></div>
<div class="card-footer"><div><small><b>${ev.pass}</b> events passed</small></div>
<div><small><b>${ev.fail}</b> events failed, <b>${ev.total - ev.pass - ev.fail}</b> others</small></div></div>
</div></div>
</div>
<div class="row"><div class="col-md-12">
<div class="card"><div class="card-header"><p>Timeline</p></div>
<div class="card-body pt-0"><div><canvas id="timeline" height="120"></canvas></div></div>
</div></div></div>
<script>
var timeline = {
${timeline}
};
</script>
<div class="row">
</div>
</div>
<script>
var statusGroup = {
parentCount: ${CASES.length},
passParent: ${tests.pass},
failParent: ${tests.fail},
warningParent: 0,
skipParent: ${tests.skip},
childCount: 0,
passChild: 0,
failChild: 0,
warningChild: 0,
skipChild: 0,
infoChild: 0,
grandChildCount: 0,
passGrandChild: 0,
failGrandChild: 0,
warningGrandChild: 0,
skipGrandChild: 0,
infoGrandChild: 0,
eventsCount: ${ev.total},
passEvents: ${ev.pass},
failEvents: ${ev.fail},
warningEvents: ${ev.warn},
skipEvents: ${ev.skip},
infoEvents: ${ev.info}
};
</script>        </div>
      </div>
    </div>
  </div>
<script src="https://cdn.jsdelivr.net/gh/extent-framework/extent-github-cdn@c05cd28cde1617b9d0c05a831daff6cb97fd9fd5/spark/js/spark-script.js"></script>
<script type="text/javascript"></script></body>
</html>
`;

const outFile = path.join(dir, REPORT_NAME + '.html');
fs.writeFileSync(outFile, head + body, 'utf8');
console.log(outFile, JSON.stringify({ ev, tests, durations: built.map(b => b.c.tag + ' ' + dur(b.log[0].ms, b.log.slice(-1)[0].ms)) }));

