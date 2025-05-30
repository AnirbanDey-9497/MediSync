package com.pm.stack;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.ec2.*;
import software.amazon.awscdk.services.ec2.InstanceType;
import software.amazon.awscdk.services.ecs.*;
import software.amazon.awscdk.services.ecs.Protocol;
import software.amazon.awscdk.services.ecs.patterns.ApplicationLoadBalancedFargateService;
import software.amazon.awscdk.services.logs.LogGroup;
import software.amazon.awscdk.services.logs.RetentionDays;
import software.amazon.awscdk.services.msk.CfnCluster;
import software.amazon.awscdk.services.rds.*;
import software.amazon.awscdk.services.route53.CfnHealthCheck;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class LocalStack extends Stack {

    private final Vpc vpc;
    private final Cluster ecsCluster;

    public LocalStack(final App scope, final String id, final StackProps props)
    {
        super(scope, id, props);

        this.vpc= createVpc();

        DatabaseInstance authServiceDb = createDatabase("AuthServiceDB", "auth-service-db");

        DatabaseInstance patientServiceDb = createDatabase("PatientServiceDB", "patient-service-db");

        CfnHealthCheck authDBHealthCheck = createDbHealthCheck(authServiceDb, "AuthServiceDVHealthCheck");

        CfnHealthCheck patientDbHealthCheck = createDbHealthCheck(patientServiceDb, "PatientServiceDBHealthCheck");

        CfnCluster mskCluster = createMskCluster();

        this.ecsCluster = createEcsCluster();

        FargateService authService = createFargateService("AuthService", "auth-service", List.of(4005),authServiceDb,Map.of("JWT_SECRET", "501c1eb77cf533100927ac3b547fa91c6e639ddaed7efcaf4a8a0b5fd312d4d1d6589a7b97a2f87b1c7f8bdfe5fdac696918946ded38793bb50fa8cb1640cbe691fb0fbe721974f26a0dc08073fe8828fc33a6ee00ab763835120b404012a24a0f2034d175f883f0293cd0316b60341ed94790e2fe5ce39703246638531f9f9f51c41bb1a1da08c9aa01858600a5ee2c363d929d40c688b30c8361500a5c124724bb236415bd6b3791055d2902dd4b5548ac6105c2604f18a564d77baa0c0a6112c88a6e9631c961da17cd1085bae8a0c99e81aa34bad35b157c23540811a47c543171602b69f38c93fb188ade8756b7c79af30cfa010be236bb7b88d7d4c627"));

    authService.getNode().addDependency(authDBHealthCheck);
    authService.getNode().addDependency(authServiceDb);

    FargateService billingService = createFargateService("BillingService", "billing-service", List.of(4001, 9001),null,null);

        FargateService analyticsService =
                createFargateService("AnalyticsService",
                        "analytics-service",
                        List.of(4002),
                        null,
                        null);

        analyticsService.getNode().addDependency(mskCluster);

        FargateService patientService = createFargateService("PatientService",
                "patient-service",
                List.of(4000),
                patientServiceDb,
                Map.of(
                        "BILLING_SERVICE_ADDRESS", "host.docker.internal",
                        "BILLING_SERVICE_GRPC_PORT", "9001"
                ));
        patientService.getNode().addDependency(patientServiceDb);
        patientService.getNode().addDependency(patientDbHealthCheck);
        patientService.getNode().addDependency(billingService);
        patientService.getNode().addDependency(mskCluster);


        createApiGatewayService();
    }



    private CfnHealthCheck createDbHealthCheck(DatabaseInstance db, String id)
    {
        return CfnHealthCheck.Builder.create(this,id)
                .healthCheckConfig(CfnHealthCheck.HealthCheckConfigProperty.builder()
                        .type("TCP")
                        .port(Token.asNumber(db.getDbInstanceEndpointPort()))
                        .ipAddress(db.getDbInstanceEndpointAddress())
                        .requestInterval(30)
                        .failureThreshold(3)
                        .build())
                .build();
    }

    private Vpc createVpc() {
       return Vpc.Builder.create(this, "PatientManagementVPC").vpcName("PatientManagementVPC").maxAzs(2).build();
    }

    private DatabaseInstance createDatabase(String id, String dbName)
    {
        return DatabaseInstance.Builder.create(this,id)
                .engine(DatabaseInstanceEngine.postgres(PostgresInstanceEngineProps.builder()
                        .version(PostgresEngineVersion.VER_17_2).build()))
                .vpc(vpc)
                .instanceType(InstanceType.of(InstanceClass.BURSTABLE2, InstanceSize.MICRO))
                .allocatedStorage(20)
                .credentials(Credentials.fromGeneratedSecret("admin_user"))
                .databaseName(dbName)
                .removalPolicy(RemovalPolicy.DESTROY)
                .build();
    }

    private CfnCluster createMskCluster() {
        return CfnCluster.Builder.create(this, "MskCluster")
                .clusterName("Kafka-cluster")
                .kafkaVersion("2.8.0")
                .numberOfBrokerNodes(1)
                .brokerNodeGroupInfo(CfnCluster.BrokerNodeGroupInfoProperty
                        .builder().instanceType("kafka.m5.xlarge")
                        .clientSubnets(vpc.getPrivateSubnets().stream()
                                .map(ISubnet::getSubnetId)
                                .collect(Collectors.toList()))
                        .brokerAzDistribution("DEFAULT").build()).build();

    }

    private Cluster createEcsCluster() {
        return Cluster.Builder.create(this, "PatientManagementCluster")
                .vpc(vpc)
                .defaultCloudMapNamespace(CloudMapNamespaceOptions.builder().name("patient-management.local").build()).build();
    }

    private FargateService createFargateService(String id, String imageName, List<Integer> ports, DatabaseInstance db, Map<String, String> additionalEnvVars){
        FargateTaskDefinition taskDefinition= FargateTaskDefinition.Builder.create(this, id + "Task").cpu(256).memoryLimitMiB(512).build();

        ContainerDefinitionOptions.Builder containerOptions =
        ContainerDefinitionOptions.builder()
                .image(ContainerImage.fromRegistry(imageName))
                .portMappings(ports.stream().map(port -> PortMapping.builder()
                        .containerPort(port)
                        .hostPort(port)
                        .protocol(Protocol.TCP)
                        .build()).toList())
                .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder.create(this, id+"LogGroup")
                                        .logGroupName("/ecs/"+ imageName)
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY)
                                        .build())
                                .streamPrefix(imageName)
                        .build()));

        Map<String, String> envVars = new HashMap<>();

        envVars.put("SPRING_KAFKA_BOOTSTRAP_SERVERS", "localhost.localstack.cloud:4510, localhost.localstack.cloud:4511, localhost.localstack.cloud:4512");
    if(additionalEnvVars != null){
        envVars.putAll(additionalEnvVars);
    }

    if(db != null)
    {
        envVars.put("SPRING_DATASOURCE_URL", "jdbc:postgresql://%s:%s/%s-db".formatted(db.getDbInstanceEndpointAddress(),
                db.getDbInstanceEndpointPort(), imageName));

        envVars.put("SPRING_DATASOURCE_USERNAME","admin_user");
        envVars.put("SPRING_DATASOURCE_PASSWORD", db.getSecret().secretValueFromJson("password").toString());
        envVars.put("SPRING_JPA_HIBERNATE_DDL_AUTO", "update");
        envVars.put("SPRING_SQL_INIT_MODE", "always");
        envVars.put("SPRING_DATASOURCE_HIKARI_INITIALIZATION_FAIL_TIMEOUT", "60000");
    }
    containerOptions.environment(envVars);
    taskDefinition.addContainer(imageName + "Container", containerOptions.build());

    return FargateService.Builder.create(this,id).cluster(ecsCluster).taskDefinition(taskDefinition).assignPublicIp(false).serviceName(imageName).build();

    }

    private void createApiGatewayService() {
        FargateTaskDefinition taskDefinition =
                FargateTaskDefinition.Builder.create(this, "APIGatewayTaskDefinition")
                        .cpu(256)
                        .memoryLimitMiB(512)
                        .build();

        ContainerDefinitionOptions containerOptions =
                ContainerDefinitionOptions.builder()
                        .image(ContainerImage.fromRegistry("api-gateway"))
                        .environment(Map.of(
                                "SPRING_PROFILES_ACTIVE", "prod",
                                "AUTH_SERVICE_URL", "http://host.docker.internal:4005"
                        ))
                        .portMappings(List.of(4004).stream()
                                .map(port -> PortMapping.builder()
                                        .containerPort(port)
                                        .hostPort(port)
                                        .protocol(Protocol.TCP)
                                        .build())
                                .toList())
                        .logging(LogDriver.awsLogs(AwsLogDriverProps.builder()
                                .logGroup(LogGroup.Builder.create(this, "ApiGatewayLogGroup")
                                        .logGroupName("/ecs/api-gateway")
                                        .removalPolicy(RemovalPolicy.DESTROY)
                                        .retention(RetentionDays.ONE_DAY)
                                        .build())
                                .streamPrefix("api-gateway")
                                .build()))
                        .build();


        taskDefinition.addContainer("APIGatewayContainer", containerOptions);

        ApplicationLoadBalancedFargateService apiGateway =
                ApplicationLoadBalancedFargateService.Builder.create(this, "APIGatewayService")
                        .cluster(ecsCluster)
                        .serviceName("api-gateway")
                        .taskDefinition(taskDefinition)
                        .desiredCount(1)
                        .healthCheckGracePeriod(Duration.seconds(60))
                        .build();
    }

    public static void main(final String[] args) {
        App app=new App(AppProps.builder().outdir("./cdk.out").build());

        StackProps props= StackProps.builder().synthesizer(new BootstraplessSynthesizer()).build();

        new LocalStack(app, "localstack", props);
        app.synth();
        System.out.println("App synthesizing in progress...");
    }
}
