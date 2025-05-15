# Compile both files with the JSON library
javac -cp .:json.jar MetaDefenderServer.java MetaDefenderClient.java

# Second terminal - run the client (in a new terminal window)
java -cp .:json.jar MetaDefenderClient
