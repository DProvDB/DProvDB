# DProvDB

Main repository for "DProvDB: Differentially Private Query Processing with Multi-Analyst Provenance" (in submission to SIGMOD 2024)

## Repository Structure

    ├── DProvDB/
    │   ├── src/                              *Main directory
    |       ├── main/scala/DProvDB/           *System source code
    |       └── test/resources/schema.yaml    *Database configuration
    │   ├── data/                             *Directory to dataset and place experimental results
    |   ├── chorus/                           *Chorus submodule
    |   ├── DProvDB_full.pdf                  *Technical report
    |   └── build.sbt                         *Project dependency


## Code Guide

#### Prerequisite:
> Database: Postgresql
> 
> Java packaging (MVN and SBT) latest version
> 
> scalaVersion := "2.12.2"

#### Step 0: Clone this repo and open

(skipped :)

#### Step 1: installing submodule (Chorus)

Clone Chorus from their github repo:
> git submodule add https://github.com/uvm-plaid/chorus.git chorus
>
> git submodule update --init --recursive

Install Chorus:

> cd chorus
> 
> mvn install


Note: potential missing dependency for Chorus.
Adding the following to the SBT file.
> libraryDependencies += "com.google.guava" % "guava" % "28.0-jre"

#### Step 2: preparing data

1) Configuring Postgres DB

> createdb adult
>
> psql adult

Create dataset schema and load data.
>CREATE TABLE adult (AGE INTEGER NOT NULL,\
>WORKCLASS VARCHAR(55) NOT NULL,\
>FNLWQT INTEGER NOT NULL,\
>EDUCATION VARCHAR(55) NOT NULL,\
>EDUCATION_NUM INTEGER NOT NULL,\
>MARITAL_STATUS VARCHAR(55) NOT NULL,\
>OCCUPATION VARCHAR(55) NOT NULL, \
>RELATIONSHIP VARCHAR(55) NOT NULL,\
RACE VARCHAR(55) NOT NULL,\
SEX VARCHAR(55) NOT NULL,\
CAPITAL_GAIN INTEGER NOT NULL,\
CAPITAL_LOSS INTEGER NOT NULL,\
HOURS_PER_WEEK INTEGER NOT NULL,\
NATIVE_COUNTRY VARCHAR(55) NOT NULL,\
SALARY VARCHAR(55) NOT NULL);
>
> \copy adult FROM './data/adult.data' DELIMITER ',' CSV

Then create a user, Link, with Postgres, and grant access:

>CREATE USER link WITH PASSWORD '12345';\
GRANT ALL PRIVILEGES ON TABLE adult TO link;


Similarly, one can import TPC-H dataset with [TPC-H kit](https://ankane.org/tpc-h).

One can also use their own data with DProvDB, but the DB schema needs to be properly configured in 'src/test/resources/schema.yaml'.

#### Step 3: running experiments

We use SBT commandline arguments tool for specifying experiments.

> sbt "run [args1] [args2] [args3] [args4]"
> 

We enable 4 arguments: 
- [args1]: dataset, must be "adult" or "tpch";
- [args2]: task, must be "RRQ" or "EQW";
- [args3]: table, e.g., "adult", or "orders";
- [args4]: 5 letters to decide which experiment(s) to run, "T" for run, "F" for not run. e.g., "TFTFT" meaning running all experiments except the 2nd and the 4th.
