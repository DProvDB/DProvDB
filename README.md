
[![arXiv](https://img.shields.io/badge/arXiv-2309.10240-b31b1b.svg)](https://arxiv.org/abs/2309.10240) [![License](https://img.shields.io/badge/License-BSD_3--Clause-blue.svg)](https://opensource.org/licenses/BSD-3-Clause) [![conference](https://img.shields.io/badge/SIGMOD--2024-Accepted-success)](https://2024.sigmod.org/index.shtml)

  

# DProvDB

  

<p  align="center">

<img  src="https://github.com/DProvDB/DProvDB/assets/28619915/7b577462-141e-4613-9210-84d9bc1524f2"  width=150  height=150>

</p>

  
  
  

Main repository for "DProvDB: Differentially Private Query Processing with Multi-Analyst Provenance", accepted to appear in Proc. of the ACM on Management of Data (PACMMOD/SIGMOD'2024) [[bibtex](#citation)] [[tech report](https://arxiv.org/abs/2309.10240)]

  
  

## Brief Intro

  

DProvDB projects aims to build an online DP query processing system where multiple data analysts (with different trust levels) are involved -- these data analysts are not allowed to collude by law or regulations but have the incentive to collude (for a more accurate query answer). We would like to develop DP algorithms that can minimize the privacy loss when the analysts are compromised and build a system &ndash; DProvDB &ndash; to maximize the total number of queries that can be answered given a fixed privacy budget.

  

This repository contains the implementation of the DProvDB system.

  

<p  align="center">

<img  src="https://github.com/DProvDB/DProvDB/assets/28619915/53c0a66c-333d-4cfc-a17a-2d376e3d36b9"  width=800>

</p>

  
  

# TL’DR

  

We provide automatic scripts for installing DProvDB, running it, and reproducing the experimental results presented in the paper.

  

```markdown

# install prerequisite packages and DProvDB

bash ./install.sh

  

# run DProvDB

bash ./run.sh

  

# plot results

bash ./plot.sh

```

  

Note that the experiment code can run for a while (typically 1-2 hours since it contains 4 runs for all experiments). Then, the plots for the paper can be found in the “/plot_code” directory.

  

If you want to manually install and run or test with your own test cases, we suggest reading the rest of the Code Guide.

  

# Repository Structure

  

```markdown

├── DProvDB/

│ ├── src/ *Main directory

| ├── main/scala/DProvDB/ *System source code

| └── test/resources/schema.yaml *Database configuration

│ ├── data/ *Directory to dataset and place experimental results (automatically)

| ├── chorus/ *Chorus submodule (w. git)

| ├── plot_code/ *plotting code

| └── build.sbt *Project dependency

```

  

# Installation Guide

  

## Hardware Requirements

  

- This codebase has been tested on Linux (Ubuntu 22.04), Mac (M1 chip), and Windows 10 (w. WSL) platforms. **We recommend Linux** for a stable testbed, and the following code guides are for Linux.

- The system code is written in Scala/Java and includes some scripts written in Python. So this codebase should be independent of specific hardware requirements.

-  **16 GB RAM** should be sufficient for reproducing the experimental results for this codebase.

  

## Datasets

  

The experimental results are evaluated over two datasets:

  

- The **Adult dataset** from UCI Machine Learning Repository (accessible in the code repository).

- The **TPC-H dataset** generated using the TPC-H kit with 1 GB scale factor (https://github.com/gregrahn/tpch-kit).

  

## Software Requirements

  

- Java Runtime Environment w. packaging tools MVN and SBT (**both**).

- Scala version =2.12.2

- PostgreSQL version=16.1

- CMake, GCC (for generating TPC-H dataset)

  

## Installing DProvDB

  

- First, clone and open the codebase.

  

```markdown

git clone https://github.com/DProvDB/DProvDB.git

cd DProvDB

```

  

- Install Chorus as a submodule

  

```markdown

git submodule add https://github.com/uvm-plaid/chorus.git chorus

git submodule update --init --recursive

  

cd chorus

mvn install

cd ..

```

Note: potential missing dependencies for Chorus. If there is an error, add the following to the SBT file.

> libraryDependencies += "com.google.guava" % "guava" % "28.0-jre"
  
 If slf4j version is not compatible with the platform, try changing the "org.slf4j" package version in "chorus/pom.xml" version to "1.7.13" (available: Sept, 2024).

- Preparing data (i.e., load into PostgreSQL), using Adult as an example (Similarly, import TPC-H dataset with [TPC-H kit](https://ankane.org/tpc-h)).

  

```markdown

createdb adult

psql adult

  

CREATE TABLE adult (AGE INTEGER NOT NULL,

WORKCLASS VARCHAR(55) NOT NULL,

FNLWQT INTEGER NOT NULL,

EDUCATION VARCHAR(55) NOT NULL,

EDUCATION_NUM INTEGER NOT NULL,

MARITAL_STATUS VARCHAR(55) NOT NULL,

OCCUPATION VARCHAR(55) NOT NULL,

RELATIONSHIP VARCHAR(55) NOT NULL,

RACE VARCHAR(55) NOT NULL,

SEX VARCHAR(55) NOT NULL,

CAPITAL_GAIN INTEGER NOT NULL,

CAPITAL_LOSS INTEGER NOT NULL,

HOURS_PER_WEEK INTEGER NOT NULL,

NATIVE_COUNTRY VARCHAR(55) NOT NULL,

SALARY VARCHAR(55) NOT NULL);

  

\copy adult FROM './data/adult.data' DELIMITER ',' CSV

  

CREATE USER link WITH PASSWORD '12345';

GRANT ALL PRIVILEGES ON TABLE adult TO link;

exit;
```

  

Note: This testing PostgreSQL is listening to the default port 5432; **If you are using another port or another username/password**, please update line 40-42 in ‘src/main/scala/Experiments/Experiments.scala’ accordingly.

  

One can also use their own data with DProvDB, but the DB schema needs to be properly configured in 'src/test/resources/schema.yaml'.

  

# Testing with DProvDB

  

## Manually Run and Evaluate DProvDB

  

### Running DProvDB Code

  

Under ‘/DProvDB’ directory, run the following command with args filled in.

  

We enable four arguments:

  

- [args1]: dataset, must be "adult" or "tpch";

- [args2]: task, must be "RRQ" or "EQW";

- [args3]: table, e.g., "adult", or "orders";

- [args4]: 5 letters to decide which experiment(s) to run, "T" for run, "F" for not run. e.g., "TFTFT" meaning running all experiments except the 2nd and the 4th.

  

```markdown

sbt "run [args1] [args2] [args3] [args4]"

```

  

For example, to run all five experiments on the adult dataset using RRQ, use

  

```markdown

sbt "run adult RRQ adult TTTTT"

```

  

Note: the experimental results data file is automatically stored in ‘data/’.

  
  

## <a name="citation"></a>How to cite:

  

> ```

> @inproceedings{zhang2024dprovdb,

> author={Zhang, Shufan and He, Xi},

> title={DProvDB: Differentially Private Query Processing with Multi-Analyst Provenance},

> journal={Proceedings of the ACM on Management of Data (SIGMOD'2024)},

> url={https://arxiv.org/abs/2309.10240},

> note={to appear}

>}

> ```

  

## Correspondence

  

[:mailbox_with_mail: Shufan Zhang](mailto:shufan.zhang@uwaterloo.ca) [:scroll: Homepage](https://cs.uwaterloo.ca/~s693zhan/) <br>

[:mailbox_with_mail: Xi He](mailto:xihe@uwaterloo.ca) [:scroll: Homepage](https://cs.uwaterloo.ca/~xihe/) <br>

  
  

## License

  

[BSD-3-Clause License](https://choosealicense.com/licenses/bsd-3-clause/