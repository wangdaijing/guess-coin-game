#!/bin/bash
git add .
git commit -m $1
./git.sh -i ~/.ssh/id_rsa_wdj push origin master
