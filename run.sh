CONDA_BASE=$(conda info --base) ; . $CONDA_BASE/etc/profile.d/conda.sh
ENV_NAME="DProvDB_build"

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

sbt "run adult EQW adult TTTTT"
sbt "run tpch EQW orders TTTTT"
sbt "run adult RRQ adult TTTTTT"
sbt "run tpch RRQ orders TTTTT"