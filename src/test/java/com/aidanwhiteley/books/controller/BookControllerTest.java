package com.aidanwhiteley.books.controller;

import static com.aidanwhiteley.books.controller.config.BasicAuthInsteadOfOauthWebAccess.AN_ADMIN;
import static com.aidanwhiteley.books.controller.config.BasicAuthInsteadOfOauthWebAccess.AN_EDITOR;
import static com.aidanwhiteley.books.controller.config.BasicAuthInsteadOfOauthWebAccess.PASSWORD;
import static com.aidanwhiteley.books.repository.BookRepositoryTest.J_UNIT_TESTING_FOR_BEGINNERS;
import static com.aidanwhiteley.books.util.DummyAuthenticationUtils.DUMMY_EMAIL;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.net.URI;
import java.util.List;

import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.aidanwhiteley.books.domain.Book;
import com.aidanwhiteley.books.repository.BookRepositoryTest;
import com.aidanwhiteley.books.util.IntegrationTest;
import com.jayway.jsonpath.JsonPath;

import static com.aidanwhiteley.books.util.DummyAuthenticationUtils.DUMMY_USER_FOR_TESTING_ONLY;

public class BookControllerTest extends IntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(BookControllerTest.class);

    @Autowired
    private TestRestTemplate testRestTemplate;

    @Test
    public void findBookById() {
        ResponseEntity<Book> response = postBookToServer();
        HttpHeaders headers = response.getHeaders();
        URI uri = headers.getLocation();

        Book book = testRestTemplate.getForObject(uri, Book.class);
        assertEquals(book.getId(), uri.getPath().substring(uri.getPath().lastIndexOf("/") + 1));
    }

    @Test
    public void findByAuthor() {
        postBookToServer();

        ResponseEntity<String> response = testRestTemplate.exchange("/api/books?author=" + BookRepositoryTest.DR_ZEUSS + "&page=0&size=10", HttpMethod.GET,
                null, String.class);
        assertEquals(HttpStatus.OK, response.getStatusCode());

        // Returns a "page" of books - so look for the content of the page
        List<Book> books = JsonPath.read(response.getBody(), "$.content");
        LOGGER.debug("Retrieved JSON was: " + response.getBody());

        assertTrue("No books found", books.size() > 0);
    }

    @Test
    public void testSensitiveDataNotReturnedToAnonymousUser() {
        ResponseEntity<Book> response = postBookToServer();
        String location = response.getHeaders().getLocation().toString();
        Book book = testRestTemplate.getForObject(location, Book.class);

        // Title should be available to everyone
        assertEquals(book.getTitle(), J_UNIT_TESTING_FOR_BEGINNERS);
        // Email should only be available to admins
        assertEquals(book.getCreatedBy().getEmail(), "");
    }

    @Test
    public void testSensitiveDataIsReturnedToAdminUser() {
        Book testBook = BookRepositoryTest.createTestBook();
        HttpEntity<Book> request = new HttpEntity<>(testBook);
        TestRestTemplate trtWithAuth = testRestTemplate.withBasicAuth(AN_ADMIN, PASSWORD);
        ResponseEntity<Book>  response = trtWithAuth
                .exchange("/secure/api/books", HttpMethod.POST, request, Book.class);

        String location = response.getHeaders().getLocation().toString();
        Book book = trtWithAuth.getForObject(location, Book.class);

        // Title should be available to everyone
        assertEquals(book.getTitle(), J_UNIT_TESTING_FOR_BEGINNERS);
        // Email should only be available to admins
        assertEquals(book.getCreatedBy().getEmail(), DUMMY_EMAIL);
    }
    
    @Test
    public void testUserDataIsReturnedToEditorUser() {
        Book testBook = BookRepositoryTest.createTestBook();
        HttpEntity<Book> request = new HttpEntity<>(testBook);
        TestRestTemplate trtWithAuth = testRestTemplate.withBasicAuth(AN_EDITOR, PASSWORD);
        ResponseEntity<Book>  response = trtWithAuth
                .exchange("/secure/api/books", HttpMethod.POST, request, Book.class);

        String location = response.getHeaders().getLocation().toString();
        Book book = trtWithAuth.getForObject(location, Book.class);

        // Title should be available to everyone
        assertEquals(book.getTitle(), J_UNIT_TESTING_FOR_BEGINNERS);
        // Email should only be available to admins - not editors
        assertEquals(book.getCreatedBy().getEmail(), "");
        // But the name of the person who created the Book should be available
        assertEquals(book.getCreatedBy().getFullName(), DUMMY_USER_FOR_TESTING_ONLY);

    }

    private ResponseEntity<Book> postBookToServer() {
        Book testBook = BookRepositoryTest.createTestBook();
        HttpEntity<Book> request = new HttpEntity<>(testBook);

        TestRestTemplate trtWithAuth = testRestTemplate.withBasicAuth(AN_ADMIN, PASSWORD);

        return trtWithAuth
                .exchange("/secure/api/books", HttpMethod.POST, request, Book.class);
    }

}