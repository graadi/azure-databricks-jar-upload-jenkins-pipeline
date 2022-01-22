# Azure Databricks JAR executable artifact deployment (upload) pipeline

<img src="https://github.com/graadi/azure-databricks-jar-upload-jenkins-pipeline/blob/main/images/az-db-logo.jpeg" />

The pipeline automates the upload of an executable Java artifact into Azure DataBricks.

Azure Databricks comes with a CLI tool that provides a way to interface with resources in Azure Databricks. It’s built on top of the Databricks REST API and can be used with the Workspace, DBFS, Jobs, Clusters, Libraries and Secrets API

To install the CLI, you’ll need Python version 2.7.9 and above if you’re using Python 2 or Python 3.6 and above if you’re using Python 3.

```bash
# Create a virtual environment in which you can install the Databricks CLI.
virtualenv -p /usr/bin/python2.7 databrickscli
```

```bash
# switch to the virtual environment you created.
source databrickscli/bin/activate

```
```bash
# install the Databricks CLI.
pip install databricks-cli
```
Pipeline stages diagram as follows:
