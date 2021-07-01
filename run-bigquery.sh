PROJECT=$1
DATASET=$2

exec java -Djava.util.logging.config.file=logging.properties \
  -jar ./target/google-cloud-spanner-pgadapter-2.0.0-SNAPSHOT.jar \
  --bigquery -s 5434 -p $PROJECT -i xxxxxx -d $DATASET -f SPANNER -q -r metadata/beam_query_rewrites.json
