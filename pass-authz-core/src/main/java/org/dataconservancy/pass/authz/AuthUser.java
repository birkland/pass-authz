/*
 * Copyright 2017 Johns Hopkins University
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dataconservancy.pass.authz;

import java.net.URI;

/**
 * @author apb@jhu.edu
 */
public class AuthUser {

    String name;
    String email;
    String institutionalId;
    URI id;
    boolean isFaculty;

    /**
     * boolean indicating whether a person has faculty status
     * @return the boolean
     */
    public boolean isFaculty() {
        return isFaculty;
    }

    /**
     * set a boolean indicating whether the person has facuty status
     * @param faculty
     */
    public void setFaculty(boolean faculty) {
        isFaculty = faculty;
    }

    /**
     * Get the user's email address
     * @return the email address
     */
    public String getEmail() {
        return email;
    }

    /**
     * Set the user's email address
     * @param email
     */
    public void setEmail(String email) {
        this.email = email;
    }

    /**
     * Get the institutional id for this user (for JHU it's the Jhed Id)
     * @return the institutional id
     */
    public String getInstitutionalId() {
        return institutionalId;
    }

    /**
     * Set the institutional id for this user (for JHU it's the Jhed Id)
     * @param institutionalId
     */
    public void setInstitutionalId(String institutionalId) {
        this.institutionalId = institutionalId;
    }

    /**
     * Get the repository id for the user which was assigned to this person's User object by the repository
     * @return the repository id
     */
    public URI getId() {
        return id;
    }

    /**
     * Set the id for the user which was assigned to this person's User object by the repository
     * @param id - the repository id
     */
    public void setId(URI id) {
        this.id = id;
    }

    /**
     * Get the display name
     * @return the display name
     */
    public String getName() {
        return name;
    }

    /**
     * Set the display name for the user
     * @param name - the display name
     */
    public void setName(String name) {
        this.name = name;
    }
}

