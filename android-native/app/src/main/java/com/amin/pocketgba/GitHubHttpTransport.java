package com.amin.pocketgba;

interface GitHubHttpTransport {
    GitHubHttpResponse execute(GitHubHttpRequest request) throws Exception;
}
