package com.learnmap.book;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface BookRepository extends JpaRepository<Book, Long> {

    List<Book> findByVisibilityOrOwnerIdInOrderByIdDesc(Visibility visibility, Collection<Long> ownerIds);
}
