#!/usr/bin/env sh

echo "Generating dashboards"

for fp in $(ls /input); do
    echo "Generating $fp"

    /usr/local/bin/jsonnet -J /grafonnet-lib/ /input/$fp -o /output/${fp%net}
done