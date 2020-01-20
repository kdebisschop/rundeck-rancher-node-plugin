# Rancher Nodes Plugin for Rundeck

This plugin implements Rundeck nodes for Rancher-managed Docker containers.

## Requirements

The containers must have bash installed for the Node Executer to work.

## Features

### Rancher Node Resource

Collects nodes from a Rancher controller host.

Features:

 - Project can include multiple environments.
 - API keys are not exposed in configuration.
 - Can limit selected containers to one per service.
 - Can exclude stopped containers.
 - Can exclude global containers.
 - Can exclude system containers.
 - Can apply a fixed set of tags to all selected containers.
 - Can define node attributes from container labels (configured by regex).
 - Can add tags from container labels (configured by regex).
 - Can add node description (e.g., url) via a label like "com.example.description"

Configuration:

 - Node executor has configurable timeout.
 - Authentication tokens for node executor and file copier are in password storage.
 - The path for authentication tokens is specified in the node source configuration.
 - Users will need to add those keys to storage in addition to entering them as password
   on the configuration page.


### Rancher Node Executor

Executes jobs on remote Docker containers managed by the Rancher host.

Features:

 - Can select first container in a service so only one needs to run (filter by "seen:1").
 - Reconstructs the STDERR channel that is missing in output from Rancher API.

### Rancher File Copier

Copies files to a node. Can be configured to use Rancher CLI if it is installed and
available. Otherwise assembles file from Base64-encoded parts transmitted via Rancher
API.

To distribute to all containers in a stack's service, omit the filter for "seen".

### Upgrade Service

Upgrades an existing service. Has required inputs:

 - Docker image
 - Start before stopping
 
Had many optional inputs:

 - New service labels (JSON Object)
 - New environment variables (JSON Object)
 - New secrets (list of strings)

### New Stack

Create a new stack. Has two required inputs:

 - Stack Name (string)
 - Environment ID (string)
    
Environment ID most correspond to an existing Rancher environment. Stack name must not exist in that environment.

### Add Service

Ads a service to an existing stack. Required inputs:

 - Environment ID (string)
 - Stack Name (string)
 - Service Name (string)
 - Docker image

Optional inputs:

 - Data volumes
 - OS environment
 - Service labels
 - Secrets

## Road Map

 - 0.6.6 Make File Copier binary-safe.
 - 0.7.0 Provide container upgrade node step, with ability to set labels and environment variables.
 - 0.7.1 Provide ability to remove labels and environment variables via container upgrade.
 - 0.9.x Provide reasonable if not complete test coverage prior to 1.x

## Compatibility
 
This has been tested with Rundeck 3.1.3 and Rancher 1.6.26.
