/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.ranger.patch;

import org.apache.commons.lang.StringUtils;
import org.apache.ranger.biz.XUserMgr;
import org.apache.ranger.common.RangerConstants;
import org.apache.ranger.db.RangerDaoManager;
import org.apache.ranger.entity.XXPortalUser;
import org.apache.ranger.service.XPortalUserService;
import org.apache.ranger.util.CLIUtil;
import org.apache.ranger.view.VXPortalUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

@Component
public class PatchPermissionModel_J10003 extends BaseLoader {
    private static final Logger logger = LoggerFactory.getLogger(PatchPermissionModel_J10003.class);

    private static final Charset ENCODING = StandardCharsets.UTF_8;

    private static boolean grantAllUsers;
    private static String  usersListFileName;

    @Autowired
    XUserMgr xUserMgr;

    @Autowired
    XPortalUserService xPortalUserService;

    @Autowired
    RangerDaoManager daoManager;

    public static void main(String[] args) {
        logger.info("main()");

        try {
            if (args != null && args.length > 0) {
                if (StringUtils.equalsIgnoreCase("ALL", args[0])) {
                    grantAllUsers = true;
                } else if (!StringUtils.isEmpty(args[0])) {
                    usersListFileName = args[0];
                }
            }

            PatchPermissionModel_J10003 loader = (PatchPermissionModel_J10003) CLIUtil.getBean(PatchPermissionModel_J10003.class);

            loader.init();

            while (loader.isMoreToProcess()) {
                loader.load();
            }

            logger.info("Load complete. Exiting!!!");

            System.exit(0);
        } catch (Exception e) {
            logger.error("Error loading", e);

            System.exit(1);
        }
    }

    @Override
    public void init() throws Exception {
        // Do Nothing
    }

    @Override
    public void printStats() {
    }

    @Override
    public void execLoad() {
        logger.info("==> PermissionPatch.execLoad()");

        assignPermissionToExistingUsers();

        logger.info("<== PermissionPatch.execLoad()");
    }

    public void assignPermissionToExistingUsers() {
        Long userCount         = daoManager.getXXPortalUser().getAllCount();
        Long patchModeMaxLimit = 500L;

        try {
            if (userCount != null && userCount > 0) {
                List<String> loginIdList = readUserNamesFromFile(usersListFileName);

                if (!CollectionUtils.isEmpty(loginIdList)) {
                    List<XXPortalUser> xXPortalUsers = new ArrayList<>();

                    for (String loginId : loginIdList) {
                        try {
                            XXPortalUser xXPortalUser = daoManager.getXXPortalUser().findByLoginId(loginId);

                            if (xXPortalUser != null) {
                                xXPortalUsers.add(xXPortalUser);
                            } else {
                                logger.info("User {} doesn't exist!", loginId);
                            }
                        } catch (Exception ex) {
                            // ignore
                        }
                    }

                    int countUserPermissionUpdated = assignPermissions(xXPortalUsers);

                    logger.info("Permissions assigned to {} of {}", countUserPermissionUpdated, loginIdList.size());
                } else if (userCount.compareTo(patchModeMaxLimit) < 0 || grantAllUsers) {
                    List<XXPortalUser> xXPortalUsers = daoManager.getXXPortalUser().findAllXPortalUser();

                    if (!CollectionUtils.isEmpty(xXPortalUsers)) {
                        int countUserPermissionUpdated = assignPermissions(xXPortalUsers);

                        logger.info("Permissions assigned to {} of {}", countUserPermissionUpdated, xXPortalUsers.size());
                    }
                } else {
                    //if total no. of users are more than 500 then process ADMIN and KEY_ADMIN users only to avoid timeout
                    List<XXPortalUser> xXPortalUsers = daoManager.getXXPortalUser().findByRole(RangerConstants.ROLE_SYS_ADMIN);

                    if (!CollectionUtils.isEmpty(xXPortalUsers)) {
                        int  countUserPermissionUpdated = assignPermissions(xXPortalUsers);

                        logger.info("Permissions assigned to users having role:{}. Processed:{} of total {}",  RangerConstants.ROLE_SYS_ADMIN, countUserPermissionUpdated, xXPortalUsers.size());
                    }

                    xXPortalUsers = daoManager.getXXPortalUser().findByRole(RangerConstants.ROLE_KEY_ADMIN);

                    if (!CollectionUtils.isEmpty(xXPortalUsers)) {
                        int  countUserPermissionUpdated = assignPermissions(xXPortalUsers);

                        logger.info("Permissions assigned to users having role:{}. Processed:{} of total {}",  RangerConstants.ROLE_SYS_ADMIN, countUserPermissionUpdated, xXPortalUsers.size());
                    }

                    logger.info("Please execute this patch separately with argument 'ALL' to assign permission to remaining users ");
                    System.out.println("Please execute this patch separately with argument 'ALL' to assign module permissions to remaining users!!");
                }
            }
        } catch (Exception ex) {
            // ignore
        }
    }

    private int assignPermissions(List<XXPortalUser> xXPortalUsers) {
        int countUserPermissionUpdated = 0;

        if (!CollectionUtils.isEmpty(xXPortalUsers)) {
            for (XXPortalUser xPortalUser : xXPortalUsers) {
                try {
                    if (xPortalUser != null) {
                        VXPortalUser vPortalUser = xPortalUserService.populateViewBean(xPortalUser);

                        if (vPortalUser != null) {
                            vPortalUser.setUserRoleList(daoManager.getXXPortalUserRole().findXPortalUserRolebyXPortalUserId(vPortalUser.getId()));

                            xUserMgr.assignPermissionToUser(vPortalUser, false);

                            countUserPermissionUpdated += 1;

                            logger.info("Permissions assigned/updated on base of User's Role, UserId [{}]", xPortalUser.getId());
                        }
                    }
                } catch (Exception ex) {
                    // ignore
                }
            }
        }

        return countUserPermissionUpdated;
    }

    private List<String> readUserNamesFromFile(String aFileName) throws IOException {
        List<String> userNames = new ArrayList<>();

        if (!StringUtils.isEmpty(aFileName)) {
            Path path = Paths.get(aFileName);

            if (Files.exists(path) && Files.isRegularFile(path)) {
                List<String> fileContents = Files.readAllLines(path, ENCODING);

                if (fileContents != null && !fileContents.isEmpty()) {
                    for (String line : fileContents) {
                        if (!StringUtils.isEmpty(line) && !userNames.contains(line)) {
                            try {
                                userNames.add(line.trim());
                            } catch (Exception ex) {
                                // ignore
                            }
                        }
                    }
                }
            }
        }

        return userNames;
    }
}
