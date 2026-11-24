package com.mzfuture.entire.common.exception;

public class Errors {
    public static final ErrorCode BAD_REQUEST = ErrorCode.of(400, "BAD_REQUEST", "Invalid request format");

    public static final ErrorCode INVALID_ARGUMENT = ErrorCode.of(400, "INVALID_ARGUMENT", "Invalid argument");

    public static final ErrorCode UNAUTHORIZED = ErrorCode.of(401, "UNAUTHORIZED", "Unauthorized");

    public static final ErrorCode ACCESS_DENIED = ErrorCode.of(403, "ACCESS_DENIED", "Access denied");

    public static final ErrorCode NOT_FOUND = ErrorCode.of(404, "NOT_FOUND", "Resource not found");

    /// Nothing to sync: the remote has no Entire checkpoint data
    public static final ErrorCode NO_CHECKPOINTS = ErrorCode.of(422, "NO_CHECKPOINTS", "No checkpoints found on remote");

    public static final ErrorCode INTERNAL_ERROR = ErrorCode.of(500, "INTERNAL_ERROR", "Internal server error");

    /// The git remote refused or failed an operation (bad token, missing permission, network).
    /// Deliberately not 401/403: the web client treats those as an expired dashboard login.
    public static final ErrorCode GIT_REMOTE_ERROR = ErrorCode.of(502, "GIT_REMOTE_ERROR", "Git remote operation failed");
}
