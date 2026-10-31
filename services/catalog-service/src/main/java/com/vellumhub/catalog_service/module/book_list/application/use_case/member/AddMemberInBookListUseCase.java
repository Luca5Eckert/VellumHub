package com.vellumhub.catalog_service.module.book_list.application.use_case.member;

import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListResourceNotFoundException;
import com.vellumhub.catalog_service.module.book_list.application.command.member.AddMemberInBookListCommand;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListPermissionDeniedException;
import com.vellumhub.catalog_service.module.book_list.domain.exception.BookListDomainException;
import com.vellumhub.catalog_service.module.book_list.domain.model.MembershipRole;
import com.vellumhub.catalog_service.module.book_list.domain.port.BookListRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
     * @throws BookListDomainException if the book list is not found or if the user doesn't have permission to add members to the book list     */
    @Transactional
    public void execute(AddMemberInBookListCommand command){
        var bookList = bookListRepository.findById(command.bookListId())
                .orElseThrow(BookListResourceNotFoundException::new);

        if(!bookList.canAddMember(command.userAuthenticatedId())) {
            throw new BookListPermissionDeniedException();
        }

        if (command.role() == MembershipRole.OWNER) {
            throw new BookListDomainException("Owner membership cannot be assigned");
        }
        bookList.addMember(command.userIdToAdd(), command.role());

        bookListRepository.save(bookList);
    }

}
