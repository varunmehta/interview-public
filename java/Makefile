.PHONY: build run seed reset test clean

build:
	./mvnw package -DskipTests

run:
	./mvnw spring-boot:run

seed:
	./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.seed=true --app.startup-check=false"

reset:
	rm -rf data
	./mvnw spring-boot:run -Dspring-boot.run.arguments="--app.seed=true --app.startup-check=false"

test:
	./mvnw test

clean:
	./mvnw clean
	rm -rf data
