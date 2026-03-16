package com.mrs.catalog_service.module.book_list.application.use_case.member;

import com.mrs.catalog_service.module.book_list.application.command.member.DeleteMemberInBookListCommand;
import com.mrs.catalog_service.module.book_list.domain.exception.BookListDomainException;
import com.mrs.catalog_service.module.book_list.domain.port.BookListRepository;
import org.springframework.stereotype.Service;

@Service
public class DeleteMemberInBookListUseCase {

    private final BookListRepository bookListRepository;

    public DeleteMemberInBookListUseCase(BookListRepository bookListRepository) {
        this.bookListRepository = bookListRepository;
    }

    public void execute(DeleteMemberInBookListCommand command) {
        var bookList = bookListRepository.findById(command.bookListId())
                .orElseThrow(() -> new BookListDomainException("Book list not found"));

        if(!bookList.canDeleteMember(command.userAuthenticatedId())) {
            throw new BookListDomainException("User don't have permission to delete member from this book list");
        }

        bookList.deleteMember(command.userIdToDelete());

        bookListRepository.save(bookList);
    }

}
