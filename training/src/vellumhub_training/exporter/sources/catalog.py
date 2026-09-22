from ..models import json_value, signal

BOOKS_SQL = """
SELECT b.id, b.title, b.description, b.author, b.release_year, b.page_count,
       b.publisher, b.updated_at,
       ARRAY(SELECT g.name FROM book_genre_id bg JOIN genres g ON g.id = bg.genre_id
             WHERE bg.book_id = b.id ORDER BY g.name COLLATE "C") AS genres
FROM books b WHERE b.deleted_at IS NULL ORDER BY b.id
"""
PROGRESS_SQL = """
SELECT id, book_id, user_id, reading_status, current_page, started_at, end_at, is_active
FROM book_progress ORDER BY user_id, book_id, id
"""


def book(row):
    return json_value(dict(bookId=row["id"], title=row["title"],
                           description=row["description"], author=row["author"],
                           genres=row["genres"], releaseYear=row["release_year"],
                           pageCount=row["page_count"], publisher=row["publisher"],
                           updatedAt=row["updated_at"]))


def reading_state(row):
    return signal(row, "READING_STATE", "catalog-service", "CANONICAL_CURRENT",
                  dict(status=row["reading_status"], currentPage=row["current_page"],
                       startedAt=row["started_at"], endedAt=row["end_at"],
                       isActive=row["is_active"]))


def read(connection):
    return ([book(row) for row in connection.execute(BOOKS_SQL)],
            [reading_state(row) for row in connection.execute(PROGRESS_SQL)])
