[![Build Status](https://github.com/malliina/pics/workflows/Test/badge.svg)](https://github.com/malliina/pics/actions)

# pics

This is a pic app. Available at [pics.malliina.com](https://pics.malliina.com).

## Development

    sbt ~start

Navigate to http://localhost:9000.

## Deployment

Push to the `master` branch. See GitHub Actions for details.

## Documentation

Check [pics-docs.malliina.com](https://pics-docs.malliina.com).

### Deploying documentation

Install dependencies:

    pip3 install mkdocs
    pip3 install mkdocs-material

To deploy the documentation site:

    mkdocs gh-deploy

GitHub Pages hosts the documentation.

## Backups

To back up pics from S3 to a local zip file, execute PowerShell script [PicsBackup.ps1](backups/PicsBackup.ps1):

    cd backups
    pwsh PicsBackup.ps1
