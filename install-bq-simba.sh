mkdir -p simba
mv Simba*.zip simba
cd simba
unzip *.zip
cd ..

mvn install:install-file \
        -Dfile=simba/GoogleBigQueryJDBC42.jar \
        -DgroupId=com.simba.googlebigquery \
        -DartifactId=googlebigquery-jdbc42 \
        -Dversion=1.0.0 \
        -Dpackaging=jar \
        -DgeneratePom=true
