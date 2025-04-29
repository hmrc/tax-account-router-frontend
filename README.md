# tax-account-router-frontend

A frontend service which provides routing and throttling functionality. 

## Run the application 

To run the application execute:

```
sbt 'run 9280'  
```

## Test the application

To test the application execute:

```
sbt clean compile coverage test it/test coverageReport
```

There is also a 'sa-data-import' folder containing scripts to upload SA data from csv files.
