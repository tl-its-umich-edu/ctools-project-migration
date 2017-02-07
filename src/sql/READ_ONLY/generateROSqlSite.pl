#!/usr/bin/env perl
use YAML qw'LoadFile';
use POSIX qw(strftime);

use strict;

## Generate sql to delete permissions based on sites, roles, and list of permissions
## to delete.  These are configured in a yml file.
## Can also generate SQL to restore the permissions and make the site writeable again.
## To do this add a line to the file of site ids that starting with TASK: followed by either
## READ_ONLY_UPDATE, READ_ONLY_LIST or READ_ONLY_RESTORE (READ_ONLY_RESTORE_LIST)

# To use, (optionally) give the name of the yml file on the command line and capture the SQL
# to be used later.  Sql will be written for the sites read from stdin and for the
# roles and permissions given in the yml file.

### TTD: ###
# allow default of task type.
# allow comments before task specifications.
# print task in output sql
# only print more than initial counts if will update tables.
# sql file name should reflect the operation.

### DONE: ###
### Get configuration from yml file.

################
## global configuration values.
our $DB_USER;
our $comma_break_rate;
our $realms_max;
our @functions;
our @roles;

##### type of operation: READ_ONLY_UPDATE, READ_ONLY_LIST, READ_ONLY_RESTORE READ_ONLY_RESTORE_LIST
# Read required task from the command line.  Calling shell script will default it if necessary.
our $task = shift;

# get configuration file name from command line
our ($yml_file) = shift || "./ROSqlSite.yml";

# read in configuration file and set values
sub configure {
  my ($db,$functions,$sites) = LoadFile($yml_file);

  # prefix for sql tables.
  $DB_USER=$db->{db_user};
  
  # for contents of IN clause how often put in a line break when generate list.
  $comma_break_rate=$db->{comma_break_rate};
  
  # how many realms to put in each separate query.
  $realms_max=$db->{realms_max};
  
  # functions (permissions) to delete.
  @functions = @{$db->{functions}};
  
  # roles to examine.
  @roles=@{$db->{roles}};

  # setup the task to be done.
  setupTask($task);
}

## Variables to hold information to go in sql.
# what sql action to do
our($sqlAction);
# what table to read from
our($READ_TABLE);
# what table to write to (if appropriate)
our($UPDATE_TABLE);
# setup values for the task.

# Given a task setup the sql variables.
sub setupTask {
  my $task = shift;
  #  print "sT: task: [$task]\n";

  die (">>>>> INVALID TASK: [$task]") unless ($task eq "READ_ONLY_UPDATE"
                                              || $task eq "READ_ONLY_LIST"
                                              || $task eq "READ_ONLY_RESTORE"
                                              || $task eq "READ_ONLY_RESTORE_LIST");
  
  # take permissions out of role function table to make site read only
  if ($task eq "READ_ONLY_UPDATE") {
    ($sqlAction,$READ_TABLE,$UPDATE_TABLE) 
      = ("DELETE ","sakai_realm_rl_fn_RD","sakai_realm_rl_fn_UP");
  }
  # list what would be removed from the table
  if ($task eq "READ_ONLY_LIST") {
    ($sqlAction,$READ_TABLE,$UPDATE_TABLE)
      = ("SELECT * ","sakai_realm_rl_fn_RD","sakai_realm_rl_fn_UP");
  }
  # restore permissions from the archive table
  if ($task eq "READ_ONLY_RESTORE") {
    ($sqlAction,$READ_TABLE,$UPDATE_TABLE) 
      = ("MERGE? INSERT?","sakai_realm_rl_fn_ARCHIVE_RD","sakai_realm_rl_fn_UP");
  }
  # list what would be restored.
  if ($task eq "READ_ONLY_RESTORE_LIST") {
    ($sqlAction,$READ_TABLE,$UPDATE_TABLE)
#      = ("SELECT * ","sakai_realm_rl_fn_ARCHIVE","sakai_realm_rl_fn");
      = ("SELECT * ","sakai_realm_rl_fn_20161214_A_RD","sakai_realm_rl_fn_20161214_A_UP");
  }
  #  print ">>>>>>>> action: [$sqlAction] READ_TABLE: [$READ_TABLE] UPDATE_TABLE: [$UPDATE_TABLE]\n";
  die "invalid task: [$task]\n" unless($sqlAction);
}

# sql to update the action log.

sub writeActionLog {
  my($task,$siteId) = @_;
  print "/****** update log table *******/\n";
  print "insert into ${DB_USER}.CPM_ACTION_LOG VALUES(CURRENT_TIMESTAMP,'${siteId}','$task');\n";
}

# sql to make a function table backup.
sub writeRRFTableBackupSql {
  my $timeStamp = strftime '%Y%m%d', gmtime();
  print "/****** make backup table ********/\n";
  print "/* script creation time and backup table id: $timeStamp */\n";
  print "create table ${DB_USER}.SAKAI_REALM_RL_FN_${timeStamp} as select * from ${DB_USER}.SAKAI_REALM_RL_FN;\n";
}

########## Methods to expand lists to SQL suitable format.

# return a string from a list of strings. Entries to be enclosed in ', separated by commas,
# and to include line break every comma_break_rate entries.

sub commaList {
  my $entry_cnt = 0;
  my $br;
  my $list_string .= "'".shift(@_)."'";
  foreach my $l (@_) {
    $br = ((++$entry_cnt % $comma_break_rate) == 0) ? "\n" : "";
    $list_string .= ",${br}'$l'";
  }
  $list_string;
}

# return a string from a list of strings.  Entries will formatted to provide a list of
# matching realms (using SQL like function).
sub unionList {
  my $break_cnt = 0;
  my $continue = "UNION ";

  my $list_string .= " " x 8 .formatRealmKey(shift(@_));
  foreach my $l (@_) {
    $list_string .= "\n ${continue} ".formatRealmKey($l);
  }
  $list_string;
}

############ assemble the sql query

sub buildSql {
  my @realmIds = @_;
  
  my $roles_as_sql = commaList(@roles);
  my $rs = role_keys_sql($roles_as_sql);

  my $functions_as_sql = commaList(@functions);
  my $fs = function_keys_sql($functions_as_sql);

  my $realms_as_sql = unionList(@realmIds);
  my $rk = realm_keys_sql($realms_as_sql);

  my $prefix = prefix_sql();
  my $suffix = suffix_sql();

  print "\n";
#  printComment("update permissions");
  print "${prefix}\n";
  print "${rs},\n";
  print "${fs},\n";
  print "${rk},\n";
  print "${suffix}\n";

}

############# return portions of query sql

## format a single realm key
sub formatRealmKey {
  my $rid = shift;
  "(SELECT realm_key FROM   ${DB_USER}.sakai_realm  WHERE  realm_id LIKE '%/$rid%')"
}

# return the sql for the start of the update query.
sub prefix_sql {
#  print "UPDATE_TABLE: ${UPDATE_TABLE}\n";
  #  print "sqlAction: ${sqlAction}\n";
  printComment("take action");
  my $sql = <<"PREFIX_SQL";
   ${sqlAction}
   FROM   ${DB_USER}.${UPDATE_TABLE} SRRF 
   WHERE  EXISTS (WITH -- look up all the internal keys from external names
PREFIX_SQL
  $sql
}

# return the sql for the role keys sub-table
sub role_keys_sql {
  my $role_as_sql = shift;
  my $sql = <<"END_ROLE_KEYS_SQL";
      role_keys 
        AS ((SELECT role_key AS role_key 
        FROM   ${DB_USER}.sakai_realm_role 
        WHERE  role_name IN (
${role_as_sql}
                             )))
END_ROLE_KEYS_SQL
  $sql
}

# return the sql for the function keys sub-table
sub function_keys_sql {
  my $function_as_sql = shift;
  my $sql = <<"FUNCTION_KEYS_SQL";
      function_keys
        AS ((SELECT function_key AS function_key
        FROM   ${DB_USER}.sakai_realm_function
        WHERE  function_name IN (
${function_as_sql}
                                 )))
FUNCTION_KEYS_SQL
  $sql
}

# return the sql for the realm keys sub-table.
sub realm_keys_sql {
  my $realm_as_sql = shift;
  my $sql = <<"REALM_KEY_SQL";
      realm_keys
        AS (
$realm_as_sql
            )
REALM_KEY_SQL
  $sql
}

# sql that uses the sub-tables to generate list of grants matching the
# role, function, realm criteria
sub suffix_sql {
  my $sql = <<"SUFFIX_SQL";
      -- generate all the possible rows to act on
      role_function_realm_keys
        AS (SELECT * FROM   role_keys, function_keys, realm_keys),
      -- find the rows that actually exist
        delete_grants
          AS (SELECT SRRF_2.*
--          FROM   ${DB_USER}.sakai_realm_rl_fn SRRF_2, role_function_realm_keys
          FROM   ${DB_USER}.${READ_TABLE} SRRF_2, role_function_realm_keys
          WHERE  SRRF_2.role_key = role_function_realm_keys.role_key
             AND SRRF_2.function_key = role_function_realm_keys.function_key
             AND SRRF_2.realm_key =  role_function_realm_keys.realm_key)
      -- use coordinated query to connect the list of rows to act on with the grant table
      SELECT realm_key,
             role_key,
             function_key
        FROM   delete_grants
        WHERE  delete_grants.realm_key = SRRF.realm_key
           AND delete_grants.role_key = SRRF.role_key
           AND delete_grants.function_key = SRRF.function_key);
SUFFIX_SQL
  $sql
}

###### sql to restore permissions

# INSERT INTO <DB_USER>.SAKAI_REALM_RL_FN
# 	select * from <DB_USER>.sakai_realm_rl_fn_ARCHIVE SRRF
#     where exists (WITH
#         realm_keys
#     	    AS ((SELECT realm_key FROM   <DB_USER>.sakai_realm  WHERE  realm_id LIKE '%/SITE_ID%'))
# 		 select * from <DB_USER>.sakai_realm_rl_fn SRRF_2, realm_keys
# 	    	where SRRF_2.REALM_KEY = realm_keys.REALM_KEY
#   	  	 	  and SRRF.REALM_KEY = SRRF_2.REALM_KEY
# 	);

# print a commented string to the sql output

# sub buildSqlRestore {
#   my(@realmIds) = @_;
#   my $realms_as_sql = unionList(@realmIds);
#   my $rk = realm_keys_sql($realms_as_sql);
#   my $sql = <<"RESTORE_SQL";
#  INSERT INTO ${DB_USER}.SAKAI_REALM_RL_FN
#  	select * from ${DB_USER}.sakai_realm_rl_fn_ARCHIVE SRRF
#      where exists (WITH
#          $rk
#  		 select * from ${DB_USER}.sakai_realm_rl_fn_ARCHIVE SRRF_2, realm_keys
#  	    	where SRRF_2.REALM_KEY = realm_keys.REALM_KEY
#    	  	 	  and SRRF.REALM_KEY = SRRF_2.REALM_KEY
#  	)
# RESTORE_SQL
#   print $sql
  
# }
sub buildSqlRestore {
  my(@realmIds) = @_;
  my $realms_as_sql = unionList(@realmIds);
  my $rk = realm_keys_sql($realms_as_sql);
  my $sql = <<"RESTORE_SQL";
-- INSERT INTO ${DB_USER}.SAKAI_REALM_RL_FN
 INSERT INTO ${DB_USER}.${UPDATE_TABLE}
-- 	select * from ${DB_USER}.sakai_realm_rl_fn_ARCHIVE SRRF
 	select * from ${DB_USER}.${READ_TABLE} SRRF
     where exists (WITH
         $rk
-- 		 select * from ${DB_USER}.sakai_realm_rl_fn_ARCHIVE SRRF_2, realm_keys
 		 select * from ${DB_USER}.${READ_TABLE} SRRF_2, realm_keys
 	    	where SRRF_2.REALM_KEY = realm_keys.REALM_KEY
   	  	 	  and SRRF.REALM_KEY = SRRF_2.REALM_KEY
 	)
RESTORE_SQL
  print $sql
  
}

# sub buildSqlList {
#   my(@realmIds) = @_;
#   my $realms_as_sql = unionList(@realmIds);
#   my $rk = realm_keys_sql($realms_as_sql);
#   my $sql = <<"RESTORE_SQL";
# HOWDY
# RESTORE_SQL
#   print $sql
  
# }

sub printComment {
  my($msg) = shift;
  print "/****** ${msg} ******/\n\n";
}

sub printPermissionsCount {
  my $msg = shift;
  #  print "/***** $msg ******/\n";

  printComment($msg);
  print "select count(*) from ${DB_USER}.SAKAI_REALM_RL_FN;\n\n";
}

### utilities to manage input / output

# sub setTaskFromFile {
#   $_ = shift;
#   chomp;
# #  print "sTFF: [$_]\n";

#   # generate proper sql
#   if (/^TASK:\s*(\S+)/i) {
# #    print "sTFF: found task: [$1]\n";
#     $task = $1;
#     die (">>>>> INVALID TASK: [$task]") unless ($task eq "READ_ONLY_UPDATE"
#                                                 || $task eq "READ_ONLY_LIST"
#                                                 || $task eq "READ_ONLY_RESTORE"
#                                                 || $task eq "READ_ONLY_RESTORE_LIST");
#  #   print "calling setupTask\n";
#     setupTask($task);
#   }
#   else {
#     die(">>>>> must specify task as first line.");
#   }
# }

sub parseSiteLine {
  $_ = shift;
#  print "pSL: [$_]\n";

  # skip empty lines and comments
  return if (/^\s*$/);
  return if (/^\s*#/);
  split(' ',$_);
}

# Filter out dull input lines and break other lines into pieces.
# sub parseSiteLine {
#   $_ = shift;
#     print "pSL: [$_]\n";

#   # default task type
#   if (!defined($_) || length($_) == 0) {
#     $_ = "TASK: READ_ONLY_UPDATE";
#   }

# print "pSL: line2 : [$_]\n";
  
#   # generate proper sql
#   if (/^TASK:\s*(\S+)/i) {
#     print "pSL: found task: [$1]\n";
#     $task = $1;
#     die (">>>>> INVALID TASK: [$task]") unless ($task eq "READ_ONLY_UPDATE"
#                                                 || $task eq "READ_ONLY_LIST"
#                                                 || $task eq "READ_ONLY_RESTORE"
#                                                 || $task eq "READ_ONLY_RESTORE_LIST");
#     print "calling setupTask\n";
#     setupTask($task);
#   }
#   print "pSL: task: [$task]\n";

#   # skip empty lines and comments
#   return if (/^\s*$/);
#   return if (/^\s*#/);
#   split(' ',$_);
# }

# print site sql if there are any sites.
sub printForSites {
  my $task = shift;
  my @realmIds = @_;
  return if ((scalar @realmIds) == 0);
  #  ???
  #  print "fPS: task: [$task] realmIds: [@realmIds]\n";
  buildSql(@realmIds) if ($task eq "READ_ONLY_UPDATE");
  buildSql(@realmIds) if ($task eq "READ_ONLY_LIST");
  buildSqlRestore(@realmIds) if ($task eq "READ_ONLY_RESTORE");
  buildSql(@realmIds) if ($task eq "READ_ONLY_RESTORE_LIST");
  }

##### Driver reads site ids from stdin #######

# read list of site ids from stdin and output sql update script.
# Will limit number of site ids in a single query to a maximum number,
# so there may be multiple queries.
sub readFromStdin {
  
  # make a backup table.
  ## make these by hand once.
  #  writeRRFTableBackupSql($task);

#  my $taskLine = <>;
#  setTaskFromFile($taskLine);
  
  printPermissionsCount("initial count");

  my @realmIds = ();
  
  while (<>) {
    #    print "line: [$_]";

    if ((scalar @realmIds) >= $realms_max) {
      printForSites($task,@realmIds);
      printPermissionsCount("updated so far");
      @realmIds = ();
    }
    chomp;
    my(@P) = parseSiteLine $_;
    next unless(defined($P[0]));
    writeActionLog($task,@P[0]) if ($task eq "READ_ONLY_UPDATE");
    # add site id  to list of realms to process.
    if ((scalar @P) == 1) {
      push @realmIds,$P[0];
    }
  }
  
  # print any trailing sites
  if ((scalar @realmIds) >= 0) {
    printForSites($task,@realmIds);
  }

  printPermissionsCount("final count");
  printComment("end");
}

#### Invoke with configuration file and list of site ids.

configure(@ARGV);
readFromStdin();

#end

############### example working query ################
# DELETE
# FROM   CTDEV_USER.sakai_realm_rl_fn SRRF 
# WHERE  EXISTS (WITH -- look up all the keys from names
# 					role_keys 
#                          AS ((SELECT role_key AS role_key 
#                          FROM   CTDEV_USER.sakai_realm_role 
#                          WHERE  role_name IN ( 'Member', 'Observer', 'Organizer', 'Owner'
#                          --WHERE  role_name IN ( 'Member', 'Observer', 'Organizer', 'Owner',
#                          -- 'Affiliate','Assistant','Instructor','Librarian','Student'
#                          ))), 
#                     function_keys 
#                          AS ((SELECT function_key AS function_key 
#                          FROM   CTDEV_USER.sakai_realm_function 
#                          WHERE  function_name IN ( 'annc.delete.any', 
#                                                    'annc.delete.own',
#                                                    'annc.new',
#                                                    'annc.revise.any',
#                                                    'annc.revise.own',
# 'calendar.delete.any','calendar.delete.own','calendar.import','calendar.new','calendar.revise.any','calendar.revise.own',
# 'chat.delete.any','chat.delete.channel','chat.delete.own','chat.new','chat.new.channel',
# 'content.delete.any','content.delete.own','content.hidden','content.new','content.revise.any','content.revise.own','content.all.groups',
# 'dropbox.maintain',
# 'mail.delete.any','mail.new',
# 'realm.del','realm.upd','realm.upd.own',
# 'site.upd','site.upd.site.mbrshp','site.upd.grp.mbrshp'
#                                                  ))),
#                     realm_keys 
#                          AS (   --    (SELECT realm_key FROM   CTDEV_USER.sakai_realm  WHERE  realm_id LIKE '%/c453ab7b-fc80-4596-0032-b89b9806e724%') 
#                               -- UNION 
#                               -- (SELECT realm_key FROM   CTDEV_USER.sakai_realm  WHERE  realm_id LIKE '%/3d716ade-1793-483e-ad0f-1e3ebb785686%') 
#                               (SELECT realm_key FROM   CTDEV_USER.sakai_realm  WHERE  realm_id LIKE '%/32510218-a785-4954-bf34-aab609b1ad49%') 
#                             ), 
#                     -- generate all the possible rows to delete
#                     role_function_realm_keys 
#                         AS (SELECT * FROM   role_keys, function_keys, realm_keys), 
#                     -- find the rows that actually exist
#                     delete_grants 
#                         AS (SELECT SRRF_2.* 
#                         -- FROM   ctdev_user.sakai_realm_rl_fn_test SRRF_2, role_function_realm_keys 
#                         FROM   CTDEV_USER.sakai_realm_rl_fn SRRF_2, role_function_realm_keys 
#                         WHERE  SRRF_2.role_key = role_function_realm_keys.role_key 
#                            AND SRRF_2.function_key = role_function_realm_keys.function_key 
#                            AND SRRF_2.realm_key =  role_function_realm_keys.realm_key)
#                     -- use coordinated query to connect the list of rows to delete with the grant table, so delete knows which to delete. 
#                SELECT realm_key, 
#                       role_key, 
#                       function_key 
#                 FROM   delete_grants 
#                 WHERE  delete_grants.realm_key = SRRF.realm_key 
#                    AND delete_grants.role_key = SRRF.role_key 
#                    AND delete_grants.function_key = SRRF.function_key)
