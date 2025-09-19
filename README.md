# Junes Online Video Game Store

Back End service for Junes prototype online video game store.

This is a dummy online website that sells video games.

## Prerequisites
```
docker
docker compose
```

## Docker
Ensure that terminal is in project directory (where `docker-compose.yml` is located) <br>
To run container, run:
```
docker compose up 
```

To stop container:
```
docker compose down
```

## MongoDB Compass
In terminal, run this command:
```
mongodb-compass
```
Manage product documents on with this GUI.

## PgAdmin
Open this URL in a browser
```
localhost:5050
```
Login with the credentials stated inside `docker-compose.yml`. <br>
Manage the user and cartItems database here.
