package com.vellumhub.engagement_service.module.book_snapshot.domain.exception;

public class BookSnapshotNotFoundException extends RuntimeException {

    public BookSnapshotNotFoundException(String message){
        super(message);
    }

    public BookSnapshotNotFoundException(){
        super("Book snapshot not found");
    }
}
