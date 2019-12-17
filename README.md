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

 - Can select first container in a service so only one needs to run.
 - Reconstructs the STDERR channel that is missing in output from Rancher API.

### Rancher File Copier

Should be considered beta. Probably limited to text files.

## Road Map

 - 0.6.x Make File Copier binary-safe.
 - 0.7 Provide container upgrade node step plugin.
 - 0.9.x Provide reasonable if not complete test coverage prior to 1.x

## Known Bugs
 
 - File Copier handles only test files.

## Compatibility
 
This has been tested with Rundeck 3.1.3 and Rancher 1.6.26.
