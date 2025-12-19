"""
Database connection for ML Service
Only connects to recommendation_db to fetch MediaFeatures
"""

import psycopg2
from psycopg2 import pool
from psycopg2.extras import RealDictCursor
import logging
from contextlib import contextmanager

logger = logging.getLogger(__name__)


class DatabaseConnection:
    """
    Manages PostgreSQL connection to recommendation_db
    ML service only accesses recommendation_db for MediaFeatures
    """
    
    def __init__(self, host, port, database, user, password, minconn=2, maxconn=10):
        """
        Initialize database connection pool for recommendation_db
        
        Args:
            host: Database host
            port: Database port
            database: Database name (should be recommendation_db)
            user: Database user
            password: Database password
            minconn: Minimum connections in pool
            maxconn: Maximum connections in pool
        """
        self.host = host
        self.port = port
        self.database = database
        
        try:
            logger.info(f"Initializing connection pool to {host}:{port}/{database}")
            self.pool = psycopg2.pool.ThreadedConnectionPool(
                minconn=minconn,
                maxconn=maxconn,
                host=host,
                port=port,
                database=database,
                user=user,
                password=password,
                connect_timeout=10
            )
            logger.info("Database connection pool initialized successfully")
        except Exception as e:
            logger.error(f"Failed to create connection pool: {str(e)}")
            raise
    
    @contextmanager
    def get_cursor(self, cursor_factory=RealDictCursor):
        """
        Context manager for database cursor
        
        Args:
            cursor_factory: Cursor factory (default: RealDictCursor for dict results)
        
        Usage:
            with db.get_cursor() as cursor:
                cursor.execute("SELECT * FROM medias_features")
                results = cursor.fetchall()
        """
        conn = None
        cursor = None
        try:
            conn = self.pool.getconn()
            cursor = conn.cursor(cursor_factory=cursor_factory)
            yield cursor
            conn.commit()
        except Exception as e:
            if conn:
                conn.rollback()
            logger.error(f"Database error: {str(e)}")
            raise
        finally:
            if cursor:
                cursor.close()
            if conn:
                self.pool.putconn(conn)
    
    def close_all_connections(self):
        """Close all connections in the pool"""
        if self.pool:
            self.pool.closeall()
            logger.info("All database connections closed")
