package com.vellumhub.catalog_service.module.book_list.application.use_case.member;

import com.vellumhub.catalog_service.module.book_list.application.command.member.DeleteMemberInBookListCommand;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListDomainException;
import com.vellumhub.catalog_service.module.book_list.domain.port.BookListRepository;
import org.springframework.stereotype.Service;

@Service
public class DeleteMemberInBookListUseCase {

    private final BookListRepository bookListRepository;

    public DeleteMemberInBookListUseCase(BookListRepository bookListRepository) {
        this.bookListRepository = bookListRepository;
    }

    /**
     * Delete a member from a book list.
     * The user must have permission to delete members from the book list.
     * @param command the command containing the information needed to delete a member from a book list
     * @throws BookListDomainException if the book list is not found or if the user doesn't have permission to delete members from the book list     */
    public void execute(DeleteMemberInBookListCommand command) {
        var bookList = bookListRepository.findById(command.bookListId())
                .orElseThrow(() -> new BookListDomainException("Book list not found"));

        if(!bookList.canDeleteMember(command.userAuthenticatedId())) {
            throw new BookListDomainException("User doesn't have permission to delete a member from this book list");
        }

        bookList.deleteMember(command.userIdToDelete());

        bookListRepository.save(bookList);
    }

}
