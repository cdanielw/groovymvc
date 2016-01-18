package groovymvc

import static groovymvc.validate.Constraints.*

/**
 * @author Daniel Wiell
 */
class Author {
    long id
    String name
    Date dateOfBirth
    List<Book> books

    static constraints = [
            name       : [notNull(), notBlank()],
            dateOfBirth: custom { it < new Date() },
            books      : every(valid())
    ]

    public String toString() {
        return "Author{" +
                "id=" + id +
                ", name='" + name + '\'' +
                ", dateOfBirth=" + dateOfBirth +
                ", books=" + books +
                '}';
    }
}

class Book {
    long id
    String title
    int year
    Author author

    static constraints = [
            title : [notNull(), notBlank(), maxLength(10)],
            year  : min(1),
            author: notNull()
    ]

    public String toString() {
        return "Book{" +
                "id=" + id +
                ", title='" + title + '\'' +
                ", year=" + year +
                ", author=" + author +
                '}';
    }
}

