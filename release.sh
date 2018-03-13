echo "releasing..."
mvn -B release:prepare -Pdo-release -DreleaseVersion=0.4.15 -DdevelopmentVersion=0.4.16-SNAPSHOT -DdryRun=true
mvn release:perform -Pdo-release