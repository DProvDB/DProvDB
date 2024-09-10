
CONDA_BASE=$(conda info --base) ; . $CONDA_BASE/etc/profile.d/conda.sh
ENV_NAME="DProvDB_build"

RED='\033[1;31m'
GREEN='\033[1;32m'
CYAN='\033[1;36m'
NC='\033[0m' # No Color

if type conda 2>/dev/null; then
    if conda info --envs | grep ${ENV_NAME}; then
      echo -e $CYAN"activating environment ${ENV_NAME}"$NC
      conda activate ${ENV_NAME}
    else
      echo
      echo -e $RED"(!) Installing the conda environment ${ENV_NAME}"$NC
      echo
      conda create -n ${ENV_NAME} python=3.8
      conda activate ${ENV_NAME}
      conda install -y conda-forge::openjdk=11
      conda install -y conda-forge::maven
      conda install -y -c conda-forge postgresql
      conda install -y conda-forge::sbt
    fi
else
    echo
    echo -e $RED"(!) Please install anaconda"$NC
    echo
    return 1  # we are source'd so we cannot use exit
fi


# install chorus
git submodule add https://github.com/uvm-plaid/chorus.git chorus
git submodule update --init --recursive

cd chorus
mvn install
cd ..

# init postgres
initdb -D mylocal_db
pg_ctl -D mylocal_db -l logfile -o "-F -p 5432" start

# create user and import adult data
createuser --encrypted link
createdb --owner=link adult
psql adult -c "ALTER USER link WITH PASSWORD '12345';"
psql adult -f data/adult.ddl
psql adult -c "\\copy adult FROM './data/adult.data' DELIMITER ',' CSV"
psql adult -c "GRANT ALL PRIVILEGES ON TABLE adult TO link;"

# for TPC-H
git clone https://github.com/gregrahn/tpch-kit.git
cd tpch-kit/dbgen
make MACHINE=LINUX DATABASE=POSTGRESQL
./dbgen -vf -s 1

createdb --owner=link tpch
psql tpch -f dss.ddl
psql tpch -c "ALTER USER link WITH PASSWORD '12345';"

for i in `ls *.tbl`; do
  table=${i/.tbl/}
  echo "Loading $table..."
  sed 's/|$//' $i > /tmp/$i
  psql tpch -q -c "TRUNCATE $table"
  psql tpch -c "\\copy $table FROM '/tmp/$i' CSV DELIMITER '|'"
done

psql tpch -c "GRANT ALL PRIVILEGES ON ALL TABLES IN SCHEMA public TO link;"

cd ../..