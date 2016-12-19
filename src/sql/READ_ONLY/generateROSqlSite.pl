#!/usr/bin/env perl
use YAML qw'LoadFile';
use POSIX qw(strftime);

## Generate sql to delete permissions based on sites, roles, and list of permissions
## to delete.  These are configured in a yml file.

# To use, (optonally) give the name of the yml file on the command line and capture the SQL
# to be used later.  Sql will be written for the sites read from stdin and for the
# roles and permissions given in the yml file.


### TTD: ###
# - read sites from stdin.
# - accept input line like <site> and use default set of roles and functions.
# - accept input line like <site> <function> <role> and ignore any defaults.
# - allow reading the instance from command line (so can default to prod but override).


my ($yml_file) = shift || "./ROSqlSite.yml";

my ($db,$functions,$sites) = LoadFile($yml_file);

my $DB_USER=$db->{db_user};

### following comments are available for debugging.
#print "DB_USER: ${DB_USER}\n";

my @functions = @{$db->{functions}};

#my @sites=@{$db->{sites}};

my @roles=@{$db->{roles}};


sub writeRRFTableBackupSql {
  my $timeStamp = strftime '%Y%m%d', gmtime();
  print "/****** make backup table ********/\n";
  print "/* script creation time and backup table id: $timeStamp */\n";
  print "create table ${DB_USER}.SAKAI_REALM_RL_FN_${timeStamp} as select * from ${DB_USER}.SAKAI_REALM_RL_FN;\n";

}

# Count matching realms, both exact and prefix.
sub writeSqlRealmCounts {
  my($site,$role,$function) = @_;
  
  my $sql = " select count(*) from ${DB_USER}.SAKAI_REALM_RL_FN "
    . "where realm_key = "
    ." (select realm_key from ${DB_USER}.SAKAI_REALM where SAKAI_REALM.realm_id = "
    ."'/site/${site}')"
    ;

  print "/***** realm equal ******/\n$sql\n";

  my $sql = " select count(*) from ${DB_USER}.SAKAI_REALM_RL_FN "
    . "where realm_key = "
    ." (select realm_key from ${DB_USER}.SAKAI_REALM where SAKAI_REALM.realm_id like "
    ."'/site/${site}')"
    ;

  print "/********* realm like ********/\n$sql\n";
  
  my $sql = " select count(*) from ${DB_USER}.SAKAI_REALM_RL_FN "
    . "where realm_key = "
    ." (select realm_key from ${DB_USER}.SAKAI_REALM where SAKAI_REALM.realm_id like "
    ."'/site/${site}%')"
    ;
  
  print "/******* realm wildcard ********/\n$sql\n";
  
}


# sample simple delete statement
# delete from CTDEV_USER.SAKAI_REALM_RL_FN 
# 	where realm_key = (select realm_key from CTDEV_USER.SAKAI_REALM where SAKAI_REALM.realm_id like '/site/17450978-d5b2-48e2-8142-83e1526b1722')
# 	and role_key = (select role_key From CTDEV_USER.SAKAI_REALM_ROLE where role_name = 'Owner')
# 	and function_key = (select function_key From CTDEV_USER.SAKAI_REALM_FUNCTION where function_name = 'signup.create.group')


# Unroll each site/role/function combination and output explicit delete sql.
sub writeSqlUnroll3 {
  my($site,$role,$function) = @_;
  my $sql = " delete from ${DB_USER}.SAKAI_REALM_RL_FN "
    . "where realm_key = "
    ." (select realm_key from ${DB_USER}.SAKAI_REALM where SAKAI_REALM.realm_id like "
    ."'/site/${site}')"
 	." and role_key = "
    ." (select role_key From ${DB_USER}.SAKAI_REALM_ROLE where role_name = '${role}')"
    ." and function_key = "
    ." (select function_key From ${DB_USER}.SAKAI_REALM_FUNCTION where function_name = '${function}')";

  #  print "sql: [$sql]";
  $sql;
}

sub printSiteSql {
  foreach $s (@sites) {
    print "/******* site: $s ******/\n";
    print "select count(*) from ${DB_USER}.SAKAI_REALM_RL_FN;\n";
    writeSqlRealmCounts($s,$r,$f).";\n";
  }
}

sub printForSite {
  my ($s) = @_;

  print "/***** initial count for site: $s ******/\n";
  print "select count(*) from ${DB_USER}.SAKAI_REALM_RL_FN;\n"; 

  foreach $r (@roles) {
    print "/**************** role: $r ******/\n";
    print "select count(*) from ${DB_USER}.SAKAI_REALM_RL_FN;\n";
    foreach $f (@functions) {
      print writeSqlUnroll3($s,$r,$f).";\n";
    }
  }
  print "/***** final count for site: $s ******/\n";
  print "select count(*) from ${DB_USER}.SAKAI_REALM_RL_FN;\n"; 
}

sub parseSiteLine {
  $_ = shift;
  # skip empty lines and comments
  return if (/^\s*$/);
  return if (/^\s*#/);
  split(' ',$_);
}

sub printMsg {
  my($msg) = shift;
  print "# ${msg}\n";
}

######## Drivers

# Print all the sql based on defaults.  The counts are there to verify that permissions
# actually got deleted.

sub combineAll {
  print "/***** initial count ******/\n";
  print "select count(*) from ${DB_USER}.SAKAI_REALM_RL_FN;\n"; 

  foreach $s (@sites) {
    print "/******* site: $s ******/\n";
    print "select count(*) from ${DB_USER}.SAKAI_REALM_RL_FN;\n";
    foreach $r (@roles) {
      print "/**************** role: $r ******/\n";
      print "select count(*) from ${DB_USER}.SAKAI_REALM_RL_FN;\n";
      foreach $f (@functions) {
        print writeSqlUnroll3($s,$r,$f).";\n";
      }
    }
  }
  print "/***** final count ******/\n";
  print "select count(*) from ${DB_USER}.SAKAI_REALM_RL_FN;\n"; 
}


##### Main driver reads from stdin #######
sub readFromStdin {

  # make a backup table.
  writeRRFTableBackupSql;

  print "/***** initial count ******/\n";
  print "select count(*) from ${DB_USER}.SAKAI_REALM_RL_FN;\n";
  
  while (<>) {
    chomp;
    my(@P) = parseSiteLine $_;
    next unless(defined($P[0]));

    if ((scalar @P) == 1) {
      printForSite $P[0];
    }
  }
  print "/***** final count ******/\n";
  print "select count(*) from ${DB_USER}.SAKAI_REALM_RL_FN;\n";

}

#### Invoke
#combineAll
readFromStdin();
#printSiteSql;
#end
