package com.mrs.catalog_service.module.book_request.domain.use_case;

import com.mrs.catalog_service.module.book_request.domain.command.DeleteBookRequestCommand;
import com.mrs.catalog_service.module.book_request.domain.exception.BookRequestNotFoundException;
import com.mrs.catalog_service.module.book_request.domain.port.BookRequestRepository;
import org.springframework.stereotype.Component;

@Component
public class DeleteBookRequestUseCase {

    private final BookRequestRepository bookRequestRepository;

    public DeleteBookRequestUseCase(BookRequestRepository bookRequestRepository) {
        this.bookRequestRepository = bookRequestRepository;
    }

    public void execute(DeleteBookRequestCommand command){
        if(!bookRequestRepository.existsById(command.bookRequestId())){
            throw new BookRequestNotFoundException();
        }

        bookRequestRepository.deleteById(command.bookRequestId());
    }

}
