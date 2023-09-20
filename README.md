[![arXiv](https://img.shields.io/badge/arXiv-2309.10240-b31b1b.svg)](https://arxiv.org/abs/2309.10240)  [![License](https://img.shields.io/badge/License-BSD_3--Clause-blue.svg)](https://opensource.org/licenses/BSD-3-Clause) [![conference](https://img.shields.io/badge/SIGMOD--2024-Accepted-success)](https://2024.sigmod.org/index.shtml)

# DProvDB

<p align="center">
<img src="https://github.com/DProvDB/DProvDB/assets/28619915/7b577462-141e-4613-9210-84d9bc1524f2" width=150 height=150>
</p>



Main repository for "DProvDB: Differentially Private Query Processing with Multi-Analyst Provenance", accepted to appear in Proc. of the ACM on Management of Data (PACMMOD/SIGMOD'2024) [[bibtex](#citation)] [[tech report](https://arxiv.org/abs/2309.10240)]


## Brief Intro

DProvDB projects aims to build an online DP query processing system where multiple data analysts (with different trust levels) are involved -- these data analysts are not allowed to collude by law or regulations but have the incentive to collude (for a more accurate query answer). We would like to develop DP algorithms that can minimize the privacy loss when the analysts are compromised and build a system &ndash; DProvDB &ndash; to maximize the total number of queries that can be answered given a fixed privacy budget.

This repository contains the implementation of the DProvDB system.

<p align="center">
<img src="https://github.com/DProvDB/DProvDB/assets/28619915/53c0a66c-333d-4cfc-a17a-2d376e3d36b9" width=800>
</p>

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


## <a name="citation"></a>How to cite: 

> ```
> @inproceedings{zhang2024dprovdb,
>   author={Zhang, Shufan and He, Xi},
>   title={DProvDB: Differentially Private Query Processing with Multi-Analyst Provenance}, 
>   journal={Proceedings of the ACM on Management of Data (SIGMOD'2024)},
>   url={https://arxiv.org/abs/2309.10240},
>   note={to appear}
>}
> ```

## Correspondence

[:mailbox_with_mail: Shufan Zhang](mailto:shufan.zhang@uwaterloo.ca) [:scroll: Homepage](https://cs.uwaterloo.ca/~s693zhan/) <br>
[:mailbox_with_mail: Xi He](mailto:xihe@uwaterloo.ca) [:scroll: Homepage](https://cs.uwaterloo.ca/~xihe/) <br>


## License

[BSD-3-Clause License](https://choosealicense.com/licenses/bsd-3-clause/)

