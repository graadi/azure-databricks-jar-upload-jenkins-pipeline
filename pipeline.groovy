// -------------------------------------------------------------- //
// Jenkins declarative pipeline for uploading a Java based        //
// executable Jar file into Azure DataBricks                      //
// Upload into Azure DataBricks                                   //
//                                                                //
// Authors: Adrian Gramada                                        //
// Date:    Jan 2022                                              //
// -------------------------------------------------------------- //

/* 
The pipeline assumes the executable Jar has a source management repository.
The following script variables will store the SCM repository details so the
project can be successfully checked out, built and packed as Jar.
*/

def projectRepo
def projectRepoBranch
def projectRepoFeatureBranch
def projectName

def isSonarCheckEnabled
def sonarServerUrl
/*
The credentials must be as Jenkins credentials as a secret text or token
*/
def sonarJenkinsCredentials

def environment
def mvnProfile
def projectJarFile
def projectJarFileDbfsPath

def dataBricksInstanceUrl
def dataBricksAccessToken
def dataBricksAccessTokenId

def dataBricksResourceGroupName
def dataBricksInstanceName
def dataBricksClusterId
def dataBricksResourceId

/*
The following script variables are for internal use.
They must not be extracted as job parameters.
*/

def authorizationTokenHeader
def managementTokenHeader
def workspaceResourceIdHeader

pipeline {

    tools {
        maven 'Maven.3.5.4'
    }
    
    options {
      skipStagesAfterUnstable()
    }

    stages {

        stage('Initial Workspace Cleanup') {
            steps {
                print 'Initial Workspace Cleanup'
                deleteDir()
            }
        }

        stage('Read Parameters') {
            steps {
                script {

                    projectName = env.JOB_BASE_NAME
                    print 'Project Name: ' + projectName

                    projectRepo = "${PROJECT_GIT_REPOSITORY}"
                    echo 'Project Git Repository: ' + projectRepo
                    
                    projectRepoBranch = "${PROJECT_GIT_BRANCH}"
                    echo 'Project Git Repository: ' + projectRepoBranch
                    
                    dataBricksInstanceUrl = "${DATABRICKS_INSTANCE_URL}"
                    echo 'DataBricks Instance URL: ' + dataBricksInstanceUrl

                    dataBricksResourceGroupName = "${DATABRICKS_RESOURCE_GROUP_NAME}"
                    echo 'DataBricks resource group name: ' + dataBricksResourceGroupName
                    
                    dataBricksInstanceName = "${DATABRICKS_INSTANCE_NAME}"
                    echo 'DataBricks instance name: ' + dataBricksInstanceName

                    dataBricksClusterId = "${DATABRICKS_CLUSTER_ID}"
                    echo 'DataBricks Cluster Id: ' + dataBricksClusterId

                    dataBricksResourceId = = "${DATABRICKS_RESOURCE_ID}"
                    echo 'DataBricks Resource Id: ' + dataBricksResourceId

                    projectJarFileDbfsPath = "${DATABRICKS_DBFS_FILE_ABSOLUTE_PATH}"
                    echo 'DataBricks Jar File Absolute Path: ' + projectJarFileDbfsPath

                    try {
                        projectRepoFeatureBranch = "${PROJECT_GIT_FEATURE_BRANCH}"

                        if (projectRepoFeatureBranch?.length() > 0) {
                            echo "Project feature branch parameter has been found: " + "${PROJECT_GIT_FEATURE_BRANCH}"
                        } else {
                            projectRepoFeatureBranch = ''
                        }

                    } catch (err) {
                        projectRepoFeatureBranch = ''
                        echo "As default the project git branch paremeter will be used: " + "${PROJECT_GIT_BRANCH}"
                    }

                    try {
                        isSonarCheckEnabled = "${SONAR_CHECK_FLAG}"
                        echo "sonar definition flag: " + "${SONAR_CHECK_FLAG}"
                    } catch (err) {
                        isSonarCheckEnabled = true
                    } finally {
                        isSonarCheckEnabled = isSonarCheckEnabled.toBoolean()
                        echo '\"isSonarCheckEnabled\" (' + isSonarCheckEnabled + ') parameter has been converted to Boolean type'
                    }

                    try {
                        sonarServerUrl = "${SONAR_SERVER_URL}"
                        echo "Sonar server URL: " + "${SONAR_SERVER_URL}"

                        sonarJenkinsCredentials = "${SONAR_JENKINS_CREDENTIALS}"
                        echo "Sonar Jenkins Credentials: " + "${SONAR_JENKINS_CREDENTIALS}"
                    } catch (err) {
                        sonarServerUrl = ""
                        sonarJenkinsCredentials = ""

                        isSonarCheckEnabled = false
                        isSonarCheckEnabled = isSonarCheckEnabled.toBoolean()
                        echo '\"isSonarCheckEnabled\" (' + isSonarCheckEnabled + ') set to false as the Sonar URL is missing.'
                    }                   

                    try {
                        mvnProfile = "${MVN_PROFILE}"
                        echo "Maven Profile: " + "${MVN_PROFILE}"
                    }catch (err) {
                        mvnProfile = "TEST"
                    } finally {
                        mvnProfile = mvnProfile.toString()
                        echo '\"mvnProfile\" (' + mvnProfile + ') parameter has been converted to String type'
                    }

                    environment = mvnProfile.toUpperCase()                    
                }
            }
        }

        stage('Create SCM Folder') {
            steps {
                script {
                    sh 'mkdir ${WORKSPACE}/project_repo'
                }
            }
        }

        stage('Checkout SCM') {
            steps {
                dir("${WORKSPACE}/project_repo") {
                    script {
                        if (projectRepoFeatureBranch?.trim()) {
                            git url: projectRepo, branch: projectRepoFeatureBranch
                        } else {
                            git url: projectRepo, branch: projectRepoBranch
                        }
                    }

                    print 'Git Log After Checkout'
                    sh 'git log --pretty=format:\"%H - %cd - %an - %s\" --graph -30'

                    script {
                        if (projectRepoFeatureBranch?.trim()) {
                            sh 'git pull origin ' + projectRepoFeatureBranch
                        } else {
                            sh 'git pull origin ' + projectRepoBranch
                        }
                    }

                    print 'Git Log After Pull'
                    sh 'git log --pretty=format:\"%H - %cd - %an - %s\" --graph -30'
                }
            }
        }

        stage('Maven Build') {
            steps {
                script {
                    dir("${WORKSPACE}/project_repo") {
                        
                        def pom = readMavenPom file: 'pom.xml'
                        projectJarFile = pom.artifactId + '-' + pom.version + '-jar-with-dependencies.' + pom.packaging
                        print 'Library Filename: ' + projectJarFile

                        sh 'mvn clean install -Dmaven.test.skip=true -P' + mvnProfile
                    }
                }
            }
        }

        stage('SonarQube Quality Analysis') {
            when {
                expression {
                    isSonarCheckEnabled
                }
            }

            steps {
                script {
                    dir("${WORKSPACE}/project_repo") {
                        sh 'mvn sonar:sonar -Dsonar.host.url=' + sonarServerUrl + ' -Dsonar.login=' + sonarJenkinsCredentials
                    }
                }
            }
        }

        stage('SonarQube Quality Analysis Result Check') {
            when {
                expression {
                    isSonarCheckEnabled
                }
            }
            
            steps {
                script {

                    dir("${WORKSPACE}/project_repo/target/sonar") {

                        withSonarQubeEnv('Sequation-SonarQube') {

                            print '---------- Sonar Report Task File ----------'
                            print readFile('report-task.txt')
                            print '--------------------------------------------'

                            def props = readProperties file: 'report-task.txt'
                            def sonarServerUrl = props['serverUrl']
                            def ceTaskUrl = props['ceTaskUrl']
                            def ceTask

                            timeout(time: 2, unit: 'MINUTES') {

                                waitUntil {

                                    def response = httpRequest url: ceTaskUrl, authentication: 'sonar-jenkins'
                                    ceTask = readJSON text: response.content

                                    print '---------- Sonar ceTask File ---------------'
                                    print groovy.json.JsonOutput.prettyPrint(ceTask.toString())
                                    print '--------------------------------------------'

                                    return "SUCCESS".equals(ceTask["task"]["status"])
                                }
                            }

                            def lastAttemptResponse = httpRequest url: sonarServerUrl + "/api/qualitygates/project_status?analysisId=" + ceTask["task"]["analysisId"], authentication: 'sonar-jenkins'
                            def sonarQualityGateResultJsonObject = readJSON text: lastAttemptResponse.content
                            def sonarQualityGateResult = sonarQualityGateResultJsonObject["projectStatus"]["status"]

                            if ("ERROR".equals(sonarQualityGateResult)) {
                                error "Pipeline aborted due to SonarQube quality gate failure: ${sonarQualityGateResult}"
                            }
                        }
                    }
                }
            }
        }

        stage('Generate DataBricks Access Token') {
            steps {
                script {
                    withCredentials([azureServicePrincipal(credentialsId: 'jenkinsServicePrincipal',
                                                        subscriptionIdVariable: 'SUBS_ID',
                                                        clientIdVariable: 'CLIENT_ID',
                                                        clientSecretVariable: 'CLIENT_SECRET',
                                                        tenantIdVariable: 'TENANT_ID')]) {
                                                            
                        def azServicePrincipalLoginResponse = 
                                sh(returnStdout: true, script: "curl -X GET -H 'Content-Type: application/x-www-form-urlencoded' -d 'grant_type=client_credentials&client_id=$CLIENT_ID&resource='${dataBricksResourceId}'&client_secret=$CLIENT_SECRET' https://login.microsoftonline.com/$TENANT_ID/oauth2/token")
                        def azServicePrincipalAccessToken = retrieveTokenValue(azServicePrincipalLoginResponse, 'access_token')
                        
                        def azManagementLoginResponse = 
                                sh(returnStdout: true, script: "curl -X GET -H 'Content-Type: application/x-www-form-urlencoded' -d 'grant_type=client_credentials&client_id=$CLIENT_ID&resource=https://management.core.windows.net/&client_secret=$CLIENT_SECRET' https://login.microsoftonline.com/$TENANT_ID/oauth2/token")
                        def azManagementAccessToken = retrieveTokenValue(azManagementLoginResponse, 'access_token')
                        
                        // this is going to be the final rest call to create (and retrieve) the PAT (personal access token)
                        authorizationTokenHeader = 'Authorization: Bearer ' + azServicePrincipalAccessToken
                        managementTokenHeader = 'X-Databricks-Azure-SP-Management-Token: ' + azManagementAccessToken
                        workspaceResourceIdHeader = "X-Databricks-Azure-Workspace-Resource-Id: /subscriptions/$SUBS_ID/resourceGroups/" + dataBricksResourceGroupName + "/providers/Microsoft.Databricks/workspaces/" + dataBricksInstanceName
                        def restCallPayload = '{"lifetime_seconds": 3000, "comment": "' + currentBuild.fullDisplayName + '"}'
                        def dataBricksApiCreateTokenUrl = dataBricksInstanceUrl + '/api/2.0/token/create'

                        def dataBricksAccessTokenResponse = 
                                sh(returnStdout: true, script: "curl -X POST -H '${authorizationTokenHeader}' -H '${managementTokenHeader}' -H '${workspaceResourceIdHeader}' -d '${restCallPayload}' ${dataBricksApiCreateTokenUrl}")
                        
                        dataBricksAccessToken = retrieveTokenValue(dataBricksAccessTokenResponse, 'token_value')

                        def dataBricksAccessTokenInfo = retrieveTokenValue(dataBricksAccessTokenResponse, 'token_info')
                        dataBricksAccessTokenId = retrieveTokenValue(dataBricksAccessTokenInfo.toString(), 'token_id')

                        print "PAT: " + dataBricksAccessToken + " - PAT ID: " + dataBricksAccessTokenId
                    }
                }
            }
        }
        
        stage('DataBricks Setup') {
            steps {
                script {
                    withCredentials([azureServicePrincipal(credentialsId: 'jenkinsServicePrincipal',
                                                                    subscriptionIdVariable: 'SUBS_ID',
                                                                    clientIdVariable: 'CLIENT_ID',
                                                                    clientSecretVariable: 'CLIENT_SECRET',
                                                                    tenantIdVariable: 'TENANT_ID')]) {
                        
                        sh """#!/bin/bash

                            export PATH=$PATH:/home/jenkins/.local/bin
                            export SPARK_HOME=/home/jenkins/.local/lib/python3.6/site-packages/pyspark

                            # Configure Databricks CLI for deployment
                            echo "${dataBricksInstanceUrl}
                            ${dataBricksAccessToken}" | databricks configure --token

                            # Configure Databricks Connect for testing
                            echo "${dataBricksInstanceUrl}
                            ${dataBricksAccessToken}
                            ${dataBricksClusterId}
                            0
                            15001" | databricks-connect configure
                            
                            # Running the connect test command
                            databricks-connect test
                        """
                    }
                }
            }
        }

        stage('DataBricks Library Update') {
            steps {
                script {
                    dir("${WORKSPACE}/project_repo/target") {
                    
                        withCredentials([azureServicePrincipal(credentialsId: 'jenkinsServicePrincipal',
                                                                        subscriptionIdVariable: 'SUBS_ID',
                                                                        clientIdVariable: 'CLIENT_ID',
                                                                        clientSecretVariable: 'CLIENT_SECRET',
                                                                        tenantIdVariable: 'TENANT_ID')]) {
                            
                            sh """#!/bin/bash

                                export PATH=$PATH:/home/jenkins/.local/bin
                                export SPARK_HOME=/home/jenkins/.local/lib/python3.6/site-packages/pyspark
                                
                                printf "Started the Cluster library upload\n\n"    
                                printf "1. Uninstalled existing library\n"
                                databricks libraries uninstall --cluster-id=${dataBricksClusterId} --jar ${projectJarFileDbfsPath}

                                printf "2. Cluster has been restarted as it is required after the uninstall process\n"
                                databricks clusters restart --cluster-id=${dataBricksClusterId}

                                printf "3. Updloaded the library jar file with the latest build\n"
                                dbfs cp --overwrite ${projectJarFile} ${projectJarFileDbfsPath}

                                printf "4. Installed the newly uploaded library file into the cluster"            
                                databricks libraries install --cluster-id=${dataBricksClusterId} --jar ${projectJarFileDbfsPath}

                                printf "\n\nFinished the Cluster library upload"                                
                            """
                        }
                    }
                }
            }
        }                         
    }

    post {

        always {            
            print 'Always Post Stage.'
            script {

                if(dataBricksAccessTokenId?.length() > 0) {

                    withCredentials([azureServicePrincipal(credentialsId: 'jenkinsServicePrincipal',
                                                        subscriptionIdVariable: 'SUBS_ID',
                                                        clientIdVariable: 'CLIENT_ID',
                                                        clientSecretVariable: 'CLIENT_SECRET',
                                                        tenantIdVariable: 'TENANT_ID')]) {
                        
                        def revokeTokenRequestPayload = '{"token_id": "' + dataBricksAccessTokenId + '"}'
                        def dataBricksApiTokenRevokeUrl = dataBricksInstanceUrl + '/api/2.0/token/delete'

                        def dataBricksRevokeAccessTokenResponse = 
                                sh(returnStdout: true, script: "curl -X POST -H '${authorizationTokenHeader}' -H '${managementTokenHeader}' -H '${workspaceResourceIdHeader}' -d '${revokeTokenRequestPayload}' ${dataBricksApiTokenRevokeUrl}")
                        print groovy.json.JsonOutput.prettyPrint(dataBricksRevokeAccessTokenResponse.toString())                            
                    }                        
                }
            }            
        }

        success {
            print 'Jenkins build job has successfully finished, now performing the post actions - attach the artifact log file to the email notification.'
            script {
                withCredentials([azureServicePrincipal(credentialsId: 'jenkinsServicePrincipal',
                                                    subscriptionIdVariable: 'SUBS_ID',
                                                    clientIdVariable: 'CLIENT_ID',
                                                    clientSecretVariable: 'CLIENT_SECRET',
                                                    tenantIdVariable: 'TENANT_ID')]) {

                    def dataBricksApiTokenListUrl = dataBricksInstanceUrl + '/api/2.0/token/list'

                    def dataBricksListAccessTokenResponse = 
                            sh(returnStdout: true, script: "curl -X GET -H '${authorizationTokenHeader}' -H '${managementTokenHeader}' -H '${workspaceResourceIdHeader}' ${dataBricksApiTokenListUrl}")
                    
                    print 'Existing Pesonal Access Tokens for Jenkins Azure Principal'
                    print groovy.json.JsonOutput.prettyPrint(dataBricksListAccessTokenResponse.toString())                            
                }                 
                sendEmailNotificationWithAttachments(projectName, environment)
            }
        }

        failure {
            print 'Build has failed, sending email notification.'
            script {
                sendEmailNotificationWithAttachments(projectName, environment)
            }
        }

        unstable {
            print 'The build has become unstable, sending email notification.'
            script {
                sendEmailNotificationWithAttachments(projectName, environment)
            }
        }

        aborted {
            print 'The build has been aborded due to a failure in one of the external services. Please check the console logs.'
            script {
                sendEmailNotificationWithAttachments(projectName, environment)
            }
        }

        changed {
            print '\'changed\' is not yet implemented.'
        }

        cleanup {
            script {
                print 'Cleaning up the current build workspace folder.'
                deleteDir()
            }
        }
    }
}

void sendEmailNotificationWithAttachments(String name, String environment) {

    def emailSubject = "[" + name + " - " + environment + " - DataBricks Jar Upload] ${currentBuild.fullDisplayName}"

    emailext attachLog: true,
            body: '''${SCRIPT, template="groovy-html.template"}''',
            mimeType: 'text/html',
            subject: emailSubject,
            to: "${PROJECT_EMAIL_RECIPIENT}",
            replyTo: "${PROJECT_EMAIL_RECIPIENT}",
            recipientProviders: [[$class: 'CulpritsRecipientProvider']]
}

String retrieveTokenValue(String response, String responseKey) {
    
    print groovy.json.JsonOutput.prettyPrint(response.toString())
                            
    def responseJson = readJSON text: response

    return responseJson[responseKey]
}
