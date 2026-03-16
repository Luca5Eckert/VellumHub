package com.mrs.catalog_service.module.book_list.application.use_case.member;

import com.mrs.catalog_service.module.book_list.application.command.member.AddMemberInBookListCommand;
import com.mrs.catalog_service.module.book_list.domain.exception.BookListDomainException;
import com.mrs.catalog_service.module.book_list.domain.port.BookListRepository;
import org.springframework.stereotype.Service;

@Service
public class AddMemberInBookListUseCase {

    private final BookListRepository bookListRepository;

    public AddMemberInBookListUseCase(BookListRepository bookListRepository) {
        this.bookListRepository = bookListRepository;
    }

    /**
     * Add a member to a book list.
     * The user must have permission to add members to the book list.
     *
     * @param command the command containing the information needed to add a member to a book list
     * @throws BookListDomainException if the book list is not found or if the user don't have permission to add members to the book list
     */
    public void execute(AddMemberInBookListCommand command){
        var bookList = bookListRepository.findById(command.bookListId())
                .orElseThrow( () -> new BookListDomainException("Book list not found") );

        if(!bookList.canAddMember(command.userAuthenticatedId())) {
            throw new BookListDomainException("User don't have permission to add member to this book list");
        }

        bookList.addMember(command.userIdToAdd(), command.role());

        bookListRepository.save(bookList);
    }

}
