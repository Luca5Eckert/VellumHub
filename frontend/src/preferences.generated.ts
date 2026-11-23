export interface paths {
    "/api/v1/users/genres": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /** List Catalog genres available for onboarding */
        get: operations["genres"];
        put?: never;
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
    "/api/v1/users/me/preferences": {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        /**
         * Read own genre preferences
         * @description Returns an empty list before preferences are set.
         */
        get: operations["getOwn"];
        /**
         * Replace own genre preferences
         * @description Use the exact genre names returned by GET /users/genres.
         */
        put: operations["replaceOwn"];
        post?: never;
        delete?: never;
        options?: never;
        head?: never;
        patch?: never;
        trace?: never;
    };
}
export type webhooks = Record<string, never>;
export interface components {
    schemas: {
        ApiResponseError: {
            details?: string[];
            error?: string;
            message?: string;
            path?: string;
            /** Format: int32 */
            status?: number;
            /** Format: date-time */
            timestamp?: string;
        };
        /**
         * @example {
         *       "genres": [
         *         "Fantasy",
         *         "Science Fiction"
         *       ]
         *     }
         */
        CreateUserPreferenceRequest: {
            genres: string[];
        };
        GenreSelection: {
            genres: string[];
        };
    };
    responses: never;
    parameters: never;
    requestBodies: never;
    headers: never;
    pathItems: never;
}
export type $defs = Record<string, never>;
export interface operations {
    genres: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Catalog genres */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GenreSelection"];
                };
            };
            /** @description Missing or invalid access token */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Insufficient permission */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    getOwn: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody?: never;
        responses: {
            /** @description Current genre preferences */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GenreSelection"];
                };
            };
            /** @description Missing or invalid access token */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Insufficient permission */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
    replaceOwn: {
        parameters: {
            query?: never;
            header?: never;
            path?: never;
            cookie?: never;
        };
        requestBody: {
            content: {
                "application/json": components["schemas"]["CreateUserPreferenceRequest"];
            };
        };
        responses: {
            /** @description Preferences saved */
            200: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["GenreSelection"];
                };
            };
            /** @description Genres are empty, duplicate, or unknown */
            400: {
                headers: {
                    [name: string]: unknown;
                };
                content: {
                    "application/json": components["schemas"]["ApiResponseError"];
                };
            };
            /** @description Missing or invalid access token */
            401: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
            /** @description Insufficient permission */
            403: {
                headers: {
                    [name: string]: unknown;
                };
                content?: never;
            };
        };
    };
}
