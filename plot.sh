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
      conda install -y conda-forge::texlive-core
      conda install -y anaconda::pandas
      conda install -y anaconda::numpy
      conda install -y conda-forge::matplotlib
      conda install -y anaconda::seaborn
      conda install -y anaconda::ipykernel
      conda install -y conda-forge::pandoc
    fi
else
    echo
    echo -e $RED"(!) Please install anaconda"$NC
    echo
    return 1  # we are source'd so we cannot use exit
fi

cd plot_code
jupyter nbconvert --execute --to html Plot_EQW.ipynb
jupyter nbconvert --execute --to html Plot_conf_Ver.ipynb
jupyter nbconvert --execute --to html Table.ipynb
jupyter nbconvert --execute --to html Plot-delta.ipynb