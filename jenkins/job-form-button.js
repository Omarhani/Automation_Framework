// A "Run Job" button in the Jenkins header on every page, opening jenkins/jobForm.html as a job publishes it.
//
// Set up once:
//   1. The job publishes the jenkins/ folder with the HTML Publisher plugin: report name "Run Job", index page
//      jobForm.html (so it is served at /job/<folder>/job/<name>/Run_20Job/jobForm.html, and this file next to it).
//   2. Simple Theme plugin: Manage Jenkins -> Appearance -> Theme elements -> "Extra JavaScript URL" =
//      /job/API/job/apiCreateBooking/Run_20Job/job-form-button.js (this file, as that job publishes it).
//   3. Set PAGE_URL below to the page to open. The page's options can ride along in the query, e.g.
//      '.../jobForm.html?undoJob=/job/API/job/apiDeleteBooking&title=Create%20booking'.
// The link is relative to the Jenkins root, so it opens on whichever host Jenkins is being used from.
(function () {
  var PAGE_URL = '/job/API/job/apiCreateBooking/Run_20Job/jobForm.html';   // relative to the Jenkins root
  var LABEL = 'Run Job';
  var ID = 'job-form-button';

  function add() {
    if (document.getElementById(ID)) return;
    var host = document.querySelector('.jenkins-header__actions') || document.getElementById('page-header');
    if (!host) return;
    var root = (document.head && document.head.getAttribute('data-rooturl')) || '';
    var a = document.createElement('a');
    a.id = ID;
    a.className = 'jenkins-button jenkins-button--primary';
    a.href = root + PAGE_URL;
    a.textContent = LABEL;
    a.style.marginRight = '8px';
    host.insertBefore(a, host.firstChild);
  }
  if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', add); else add();
})();
