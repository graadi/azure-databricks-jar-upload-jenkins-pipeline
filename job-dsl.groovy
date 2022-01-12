pipelineJob("Azure DataBricks JAR Upload") {

    description("Jenkins pipeline to automate the deployment (upload) of a JAR executable file into Azure DataBricks, to be used on DataBricks pipelines processing.")
    
    logRotator {
        
        artifactDaysToKeep(0)
        artifactNumToKeep(0)
        daysToKeep(0)
        numToKeep(10)
    }

    parameters {

        stringParam( "PROJECT_GIT_REPOSITORY", "git@github.com:graadi/azure-databricks-jar-upload-jenkins-pipeline.git")
        stringParam( "PROJECT_GIT_BRANCH", "main")

        stringParam("DATABRICKS_INSTANCE_URL", "")
        stringParam("DATABRICKS_RESOURCE_GROUP_NAME", "")
        stringParam("DATABRICKS_INSTANCE_NAME", "")
        stringParam("DATABRICKS_CLUSTER_ID", "")
        stringParam("DATABRICKS_RESOURCE_ID", "")
        stringParam("DATABRICKS_DBFS_FILE_ABSOLUTE_PATH", "")

        booleanParam("SONAR_CHECK_FLAG", false, '')

        
        stringParam( "SONAR_SERVER_URL", "" )
        stringParam( "SONAR_JENKINS_CREDENTIALS", "" )

        stringParam( "MVN_PROFILE", "" )
        stringParam( "PROJECT_EMAIL_RECIPIENT", "" )
    }

    // Define the pipeline script which is located in Git
    definition {
        cpsScm {
            scm {
                git {
                    branch("master")
                    remote {
                        name("origin")
                        url("git@github.com:graadi/azure-databricks-jar-upload-jenkins-pipeline.git")
                    }
                }
            }
        // The path within source control to the pipeline jobs Jenkins file
        scriptPath("pipeline.groovy")
        }
    }
}