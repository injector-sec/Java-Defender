# Compile both files with the JSON library
javac -cp .:json.jar MetaDefenderServer.java MetaDefenderClient.java

# First terminal - run the server
java -cp .:json.jar MetaDefenderServer

# Second terminal - run the client (in a new terminal window)
