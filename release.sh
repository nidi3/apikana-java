echo "releasing..."
mvn --settings settings.xml -B release:prepare -Pdo-release -DreleaseVersion=0.4.15 -DdevelopmentVersion=0.4.16-SNAPSHOT
mvn --settings settings.xml release:perform -Pdo-release

