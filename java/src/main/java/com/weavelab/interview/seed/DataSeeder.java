package com.weavelab.interview.seed;

import com.github.javafaker.Faker;
import com.weavelab.interview.model.Contact;
import com.weavelab.interview.repository.ActivityRepository;
import com.weavelab.interview.repository.ContactRepository;
import com.weavelab.interview.repository.FileRepository;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

@Component
public class DataSeeder {

    private final ContactRepository contactRepository;
    private final FileRepository fileRepository;
    private final ActivityRepository activityRepository;

    private static final int BATCH_SIZE = 1000;

    public DataSeeder(ContactRepository contactRepository,
                      FileRepository fileRepository,
                      ActivityRepository activityRepository) {
        this.contactRepository = contactRepository;
        this.fileRepository = fileRepository;
        this.activityRepository = activityRepository;
    }

    public void seed(int contactCount, int fileCount) throws IOException {
        Faker faker = new Faker(new Random(42));
        Random rng = new Random(42);

        seedContacts(faker, contactCount);
        seedFiles(rng, fileCount);
        seedActivity(faker, rng);
    }

    private void seedContacts(Faker faker, int count) {
        for (int i = 0; i < count; i += BATCH_SIZE) {
            List<Contact> batch = new ArrayList<>();
            for (int j = 0; j < BATCH_SIZE && i + j < count; j++) {
                Contact c = new Contact();
                c.setId(UUID.randomUUID().toString());
                c.setFirstName(faker.name().firstName());
                c.setLastName(faker.name().lastName());
                c.setEmail(faker.internet().emailAddress());
                c.setPhone(faker.phoneNumber().phoneNumber());
                c.setCompany(faker.company().name());
                batch.add(c);
            }
            contactRepository.importContacts(batch);
            System.err.printf("Seeded %d/%d contacts%n", Math.min(i + BATCH_SIZE, count), count);
        }
    }

    private void seedFiles(Random rng, int count) throws IOException {
        long[] sizes = {
                1024,               // 1KB
                10 * 1024,          // 10KB
                100 * 1024,         // 100KB
                1024 * 1024,        // 1MB
                10 * 1024 * 1024,   // 10MB
                50 * 1024 * 1024    // 50MB
        };

        for (int i = 0; i < count; i++) {
            long size = sizes[rng.nextInt(sizes.length)];
            String id = String.format("file-%04d", i + 1);
            String filename = String.format("testfile-%04d.bin", i + 1);

            byte[] content = new byte[(int) size];
            rng.nextBytes(content);

            fileRepository.create(id, filename, "application/octet-stream",
                    new ByteArrayInputStream(content));
            System.err.printf("Seeded file %d/%d (%d bytes)%n", i + 1, count, size);
        }
    }

    private void seedActivity(Faker faker, Random rng) {
        String[] users = {
                "alice@example.com",
                "bob@example.com",
                "carol@example.com",
                "dave@example.com",
                "eve@example.com"
        };
        String[] actions = {"create", "update", "delete", "view", "export", "import"};
        String[] resources = {"contact", "file", "report"};

        for (int i = 0; i < 5000; i++) {
            String user = users[rng.nextInt(users.length)];
            String action = actions[rng.nextInt(actions.length)];
            String resource = resources[rng.nextInt(resources.length)];
            String resourceId = UUID.randomUUID().toString();

            activityRepository.log(user, action, resource, resourceId);
        }
        System.err.println("Seeded 5000 activity log entries");
    }

    public boolean isEmpty() {
        return contactRepository.count() == 0;
    }
}
