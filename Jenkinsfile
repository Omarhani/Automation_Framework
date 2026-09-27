// Jenkins pipeline job: pick suite, env and browser; the Extent report is published per env as "<ENV> Report"
// (HTML Publisher plugin), which is what jenkins/liveRunner.html and jenkins/jobForm.html link to.
// Run on an agent labelled "desktop" that has Java 17, Maven, Chrome / Edge (and Appium + adb for mobile suites).
pipeline {
    agent { label 'desktop' }

    parameters {
        string(name: 'SUITE', defaultValue: 'web/webSmoke', description: 'suiteFiles/<SUITE>.xml')
        choice(name: 'ENV', choices: ['Test', 'Stage'], description: 'A key of ENVIRONMENTS in data/testData.json')
        choice(name: 'BROWSER', choices: ['headlessChrome', 'chrome', 'edge', 'headlessEdge'], description: 'Web suites only')
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    stages {
        stage('Clear previous report') {
            steps {
                script {
                    if (isUnix()) { sh 'rm -rf report' } else { bat 'if exist report rmdir /s /q report' }
                }
            }
        }
        stage('Test') {
            steps {
                script {
                    // MAIL_APP_PASSWORD etc.: bind them here with withCredentials when a suite reads email
                    def cmd = "mvn -B test -Dtestng=${params.SUITE} -DserverType=${params.ENV} -DbrowserType=${params.BROWSER}"
                    if (isUnix()) { sh cmd } else { bat cmd }
                }
            }
        }
    }

    post {
        always {
            junit allowEmptyResults: true, testResults: 'target/surefire-reports/*.xml'
            publishHTML(target: [
                reportName : "${params.ENV} Report",
                reportDir  : "report/${params.ENV.toLowerCase()}Env",
                reportFiles: '**/*.html',
                keepAll    : true,
                alwaysLinkToLastBuild: true,
                allowMissing: true
            ])
        }
    }
}
