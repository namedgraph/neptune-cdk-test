package com.myorg;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import software.amazon.awscdk.core.Construct;
import software.amazon.awscdk.core.Stack;
import software.amazon.awscdk.core.StackProps;
import software.amazon.awscdk.services.ec2.ISubnet;
import software.amazon.awscdk.services.ec2.Port;
import software.amazon.awscdk.services.ec2.SecurityGroup;
import software.amazon.awscdk.services.ec2.Vpc;
import software.amazon.awscdk.services.iam.Effect;
import software.amazon.awscdk.services.iam.PolicyStatement;
import software.amazon.awscdk.services.iam.Role;
import software.amazon.awscdk.services.iam.RoleProps;
import software.amazon.awscdk.services.iam.ServicePrincipal;
import software.amazon.awscdk.services.neptune.CfnDBCluster;
import software.amazon.awscdk.services.neptune.CfnDBSubnetGroup;

public class NeptuneCdkTestStack extends Stack {
    public NeptuneCdkTestStack(final Construct scope, final String id) {
        this(scope, id, null);
    }

    public NeptuneCdkTestStack(final Construct scope, final String id, final StackProps props) {
        super(scope, id, props);
        
        Vpc vpc = Vpc.Builder.create(this, "TestVPC").maxAzs(3).build();

        SecurityGroup neptuneSg = SecurityGroup.Builder.create(this, "NeptuneSecurityGroup").
                vpc(vpc).
                securityGroupName("NeptuneSecurityGroup").
                build();
        neptuneSg.addIngressRule(neptuneSg, Port.tcp(8182));
        
        CfnDBSubnetGroup neptuneSubnetGroup = CfnDBSubnetGroup.Builder.create(this, "NeptuneSubnetGroup").
                dbSubnetGroupName("neptune-subnet-group"). // not CamelCase
                dbSubnetGroupDescription("Default subnet group").
                subnetIds(vpc.getPrivateSubnets().stream().map(ISubnet::getSubnetId).collect(Collectors.toList())).
                build();
        
        Role bulkLoaderRole = new Role(this, "RDFBulkLoaderRole", RoleProps.builder().
                assumedBy(new ServicePrincipal("rds.amazonaws.com")).
                build());
        bulkLoaderRole.addToPolicy(PolicyStatement.Builder.create().
                effect(Effect.ALLOW).
                actions(Arrays.asList("s3:Get*", "s3:List*")).
                resources(Arrays.asList("arn:aws:s3:::*")).
                build());
        
        CfnDBCluster neptuneCluster = neptuneCluster(neptuneSubnetGroup, neptuneSg, bulkLoaderRole);
    }
    
    /**
     * Setting <code>associatedRoles()</code> leads to <samp>Resolution error: Unable to resolve object tree with circular reference</samp>
     * @param subnetGroup
     * @param sg
     * @param associatedRole
     * @return 
     */
    public CfnDBCluster neptuneCluster(CfnDBSubnetGroup subnetGroup, SecurityGroup sg, Role associatedRole)
    {
        List<String> securityGroups = new ArrayList<>();
        securityGroups.add(sg.getSecurityGroupId());
        
        return CfnDBCluster.Builder.create(this, "NeptuneCluster").
                dbSubnetGroupName(subnetGroup.getDbSubnetGroupName()).
                vpcSecurityGroupIds(securityGroups).
                associatedRoles(Arrays.asList(associatedRole)). // remove and the cdk synth runs without error
                dbClusterIdentifier("OctopusTriplestoreCluster").
                iamAuthEnabled(Boolean.TRUE).
                deletionProtection(Boolean.TRUE).
                engineVersion("1.0.2.2").
                availabilityZones(getAvailabilityZones()).
                port(8182).
                deletionProtection(false).
                build();
    }
    
}
