<%-- this likely deletes all but one encounter --%>

<!DOCTYPE html PUBLIC "-//W3C//DTD XHTML 1.0 Transitional//EN"
        "http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd">
<%@ page contentType="text/html; charset=utf-8" language="java" import="org.joda.time.LocalDateTime,
org.joda.time.format.DateTimeFormatter,
org.joda.time.format.ISODateTimeFormat,java.net.*,
org.ecocean.grid.*,
org.ecocean.servlet.ServletUtilities,
java.io.*,java.util.*,
java.io.FileInputStream,
java.io.File,
java.io.FileNotFoundException,
org.ecocean.*,
javax.jdo.*,
java.lang.StringBuffer,
java.util.Vector,
java.util.Iterator,
org.ecocean.servlet.importer.*,
org.ecocean.grid.GridManager,
org.ecocean.grid.GridManagerFactory,
org.ecocean.ia.Task,
org.ecocean.servlet.importer.ImportTask,
javax.servlet.ServletConfig,
javax.servlet.ServletException,
javax.servlet.http.HttpServlet,
javax.servlet.http.HttpServletRequest,
javax.servlet.http.HttpServletResponse,
java.io.*,
java.util.ArrayList,
java.util.List,
java.util.Map,
java.util.Vector,
java.util.concurrent.ThreadPoolExecutor,
org.slf4j.Logger,
org.slf4j.LoggerFactory,
java.lang.NumberFormatException"%>

<%
String context="context0";
context=ServletUtilities.getContext(request);
Shepherd myShepherd=new Shepherd(context);
int numFixes=0;
myShepherd.beginDBTransaction();
try{
  List<String> encsToDelete = new ArrayList<String>();
  encsToDelete.add("b819ed0b-6c8b-4b63-8931-82f0f6c5c965");
  encsToDelete.add("4ef194d2-f7bc-45a9-97c5-0259b9cdabb0");
  encsToDelete.add("ff83ac2d-d805-4546-85bb-c63eaa8bdd36");
  encsToDelete.add("45f64206-63f2-40a6-9adf-a839256af1fc");
  encsToDelete.add("dc2475d9-b80f-4efd-beab-1de0059c051f");
  encsToDelete.add("4f46d209-f9c3-4920-b33a-c2d8a7dac3dd");
  encsToDelete.add("185c3a57-c755-487f-9736-084d17ec6678");
  encsToDelete.add("5b5b9cfb-d2c5-4019-8864-d2aaa95a5e04");
  encsToDelete.add("86e6f503-928b-4efc-ac42-cee6ec535d9c");
  encsToDelete.add("0e8a030e-3684-4a25-ad36-f8a67a4c3422");
  encsToDelete.add("21384b19-b03b-41aa-8fd6-f011b3ebfcf4");
  encsToDelete.add("3417b1a7-b7c8-4024-a70d-0ee44c9b1d5e");
  encsToDelete.add("d88dc525-efb9-45da-99c3-ac304ece6d23");
  encsToDelete.add("271d558c-cd57-444c-8b49-b88227fec480");
  encsToDelete.add("30c6e8c8-1886-45f0-8d02-22adb50279fa");
  encsToDelete.add("d1129e76-0373-4c61-b0a1-9216ed5a2c0f");
  encsToDelete.add("eccd61bc-d7e7-47da-81d3-03f4613bca27");
  encsToDelete.add("5fc045eb-c4f3-4582-9eed-cc57da52ab06");
  encsToDelete.add("911a3aca-8c9e-43d0-ac07-36dd5419a2b0");
  encsToDelete.add("e4ea7063-95de-4958-a325-6a7268c7e1b3");
  encsToDelete.add("809094bc-1e88-434d-8e26-ef09992aa049");
  encsToDelete.add("70ed547d-3393-43c2-bd36-f43c25c343df");
  encsToDelete.add("63110e2a-1aca-496a-925c-662899c42694");
  encsToDelete.add("89edd78d-5484-4ba3-8fc9-2b2f11254bbf");
  encsToDelete.add("a57a9dc8-8a88-4cbe-8ff1-4a67dac19127");
  encsToDelete.add("48be7d97-21cb-4953-b7ef-1ebb5dcc9dc7");
  encsToDelete.add("cb444dbd-d600-4c12-a926-fa88f618fc58");
  encsToDelete.add("2daf0c41-9498-40b9-a258-453229f91604");
  encsToDelete.add("ffd9c4a7-902f-4d91-9c96-fd531667a66d");
  encsToDelete.add("78ca751c-df57-46b5-bed4-da1dc0ba2f02");
  encsToDelete.add("5273281d-c3ba-465f-9963-044b393f4234");
  encsToDelete.add("e6e632a2-2bc3-4ddc-b676-77f7ab24db1c");
  encsToDelete.add("6be189e4-307f-4b60-9253-f680c53245e5");
  encsToDelete.add("caa1f617-fedf-4068-bcff-0d5716c92c02");
  encsToDelete.add("a61c94c7-4ff9-4727-b6a6-8f0be005122b");
  encsToDelete.add("e02c68ce-3bbf-4869-974f-f2f3abc1319c");
  encsToDelete.add("e925d407-ce44-4be4-ae3e-05422a1d90d0");
  encsToDelete.add("ebb3f061-c89e-4c8e-a446-174060c99d2a");
  encsToDelete.add("5007f5d4-45d3-4701-a82b-1353d31859cd");
  encsToDelete.add("1047802d-f1a9-43c0-be64-d35f375e367c");
  encsToDelete.add("35c82e4e-3087-4bec-be93-2d9ecb42499c");
  encsToDelete.add("9e5abfaa-b641-4353-813c-518b35dcd81d");
  encsToDelete.add("a0060f8f-b338-4cd9-a1bf-def732c6f866");
  encsToDelete.add("4ba13645-e4dd-426e-b030-db2bd0577562");
  encsToDelete.add("6403757f-5060-4754-a658-d5778a92d792");
  encsToDelete.add("64464a35-f534-45de-8eae-cc2617c6544d");
  encsToDelete.add("b3097daa-1d96-4c98-a462-e49ccb02d0fc");
  encsToDelete.add("061dce55-95da-410e-b50e-403e1e3a944b");
  encsToDelete.add("ad2f4867-1700-4d0f-bc56-b5123db22059");
  encsToDelete.add("9a95cd49-6e70-410f-9a0f-fee1528773c9");
  encsToDelete.add("b68a8bc1-6ede-4aa3-a3d1-b0df71d8a53b");
  encsToDelete.add("ba9a8a7b-a953-4d68-afbb-54eba085c932");
  encsToDelete.add("76217208-eee2-4622-b273-99c808a1774d");
  encsToDelete.add("2727af1a-d42d-4031-b011-49e6bb2c9c9a");
  encsToDelete.add("b0adf0df-f2b6-4fe4-b845-4592eb400481");
  encsToDelete.add("5186f436-90c0-47f5-81eb-344c49dbdbe6");
  encsToDelete.add("29e7a94e-3279-46ac-a895-55f961888f9d");
  encsToDelete.add("687ebd4f-e883-46d8-a9f0-50cd60d833bc");
  encsToDelete.add("c2b12e8e-ee50-4882-8d6e-58bb7df07a1e");
  encsToDelete.add("c148f700-c019-45bb-9dbd-7b0101d45c10");
  encsToDelete.add("736d21a4-71d1-4c7f-b9dc-9f771fd2eb47");
  encsToDelete.add("6e8c7fc4-f0e9-4a0c-96c8-7c3687552bcf");
  encsToDelete.add("0480ab0e-a2a1-4059-b879-9c051f72ccf7");
  encsToDelete.add("29f09c0f-d3d6-4e42-a31d-fe289d5c7db1");
  encsToDelete.add("d943246e-9b6e-4b0f-8c63-880a073b3972");
  encsToDelete.add("6d4824a2-5710-45da-ac0b-4713c5aa3ea7");
  encsToDelete.add("55cf2923-80b6-48ec-9e2f-cdaad463191b");
  encsToDelete.add("273518ef-f995-4d66-b0f2-77f17d9080c0");
  encsToDelete.add("bc0fd3e4-b6a6-4315-84cf-499d1c0c0e35");
  encsToDelete.add("a2d3c4d0-0d2a-4685-8d51-1b085ea28979");
  encsToDelete.add("478b360e-52f9-456d-8c49-fa8c1239369a");
  encsToDelete.add("422efc46-e2e4-4098-a3c6-7b7651ce1adb");
  encsToDelete.add("edcc678f-3e12-4998-9770-fc9785fc98f8");
  encsToDelete.add("a2c88f0a-9c5d-4ee6-b724-77780040c090");
  encsToDelete.add("71d039f2-debb-44b8-956f-40d05b37b7d0");
  encsToDelete.add("d7837ba0-cec2-42d4-8c57-5c173cd0bceb");
  encsToDelete.add("26062bf4-a05c-4776-a0d6-416a9514466e");
  encsToDelete.add("5726c98d-2dd2-4631-8320-7acc4102a967");
  encsToDelete.add("f8d75c4b-47a1-4388-aae6-fc3522dca9e5");
  encsToDelete.add("17472a8c-da4d-4fb2-8da6-5296829e963b");
  encsToDelete.add("a1e9f675-1f2a-40e6-988d-df501f564d3b");
  encsToDelete.add("3a8322e6-e34a-47b4-a0e1-fc8b2d22b6b0");
  encsToDelete.add("3d2ab003-75ac-4193-b86c-2178fd74ebbc");
  encsToDelete.add("cfb21fd3-a0fc-4421-b357-104d8b2ff859");
  encsToDelete.add("e8963a5f-54fc-4520-8a6d-4cd392e099a8");
  encsToDelete.add("fa585736-c2ed-4484-8e29-cadf5a525135");
  encsToDelete.add("2a975e51-0a10-4614-a1af-e8d262aab595");
  encsToDelete.add("3e6e50ef-8226-4347-a117-e979ed73afe2");
  encsToDelete.add("0dcf3b53-4246-4d5d-90eb-150224e682f1");
  encsToDelete.add("f7c74c16-3392-4cb7-9a26-4555f3283e3b");
  encsToDelete.add("c8051323-a756-49a2-af89-05b1b746518b");
  encsToDelete.add("01f660db-d02c-44ad-bc6b-cc486ce344d5");
  encsToDelete.add("2d0b583c-3a11-4b0a-9e31-0ea8d697db5c");
  encsToDelete.add("328e0a8f-b083-4956-bbd5-41d8ea2b7ec9");
  encsToDelete.add("c437c3f6-a767-48e1-8acc-4f1b24a39263");
  encsToDelete.add("bb014783-35c6-4c27-aee6-99efde9e0d26");
  encsToDelete.add("f1b21349-e96c-4c19-83fc-12a17440a546");
  encsToDelete.add("3f69b2dc-2bbf-4e6c-a999-b5a4e7d47b92");
  encsToDelete.add("001f9a83-2569-47f4-ad13-8f5e9f2eae3c");
  encsToDelete.add("5b7fdaed-edfb-470a-af51-0088db93c28c");
  encsToDelete.add("21a52370-901d-44a6-9606-0521be37fc12");
  encsToDelete.add("9a75275f-4ac1-4c8d-9bd2-32306eb32c0d");
  encsToDelete.add("b7f90f62-45f3-42ed-aab6-cf5814fae8c4");
  encsToDelete.add("a8cf3aad-cd70-4aa1-be2f-ecf71ef883f4");
  encsToDelete.add("e57e4f46-7887-4a31-a945-89d383d2030b");
  encsToDelete.add("2ffe237f-2e07-4339-98d8-214dd03b03c1");
  encsToDelete.add("52f72c83-116a-4169-8d31-bb3941a2053a");
  encsToDelete.add("11fb4936-89a2-411a-a949-88e7f368446f");
  encsToDelete.add("6c90928a-02ba-4d8f-bf18-376573a08184");
  encsToDelete.add("dce94076-43b9-4bb9-8ddb-1bf9175b4203");
  encsToDelete.add("0a5ba739-3725-463c-9839-4f270d0ba09d");
  encsToDelete.add("f89baa0c-b356-495b-b508-e64bc1e05f5f");
  encsToDelete.add("6ad6813c-4a5f-4a57-a8aa-f76df11afd22");
  encsToDelete.add("5301a855-99ba-4e8e-9442-599c7950cba4");
  encsToDelete.add("3aaa2e56-a41c-4f37-bbb1-6f2ed3e0954b");
  encsToDelete.add("152ef69a-890b-40c9-ad39-933712901abe");
  encsToDelete.add("67f77445-b679-42d0-b26d-5a1d8a4e4df7");
  encsToDelete.add("7623bcac-5580-4bdc-9ba9-7a862f090dc5");
  encsToDelete.add("9f8dbd53-c02d-4e83-b480-5dc13c95aea3");
  encsToDelete.add("647c2227-832f-4933-b39a-237e29b9b74e");
  encsToDelete.add("0d8e92f3-5497-47b8-8685-d8881a6cea38");
  encsToDelete.add("affeeb25-6db9-4cb4-87be-7faca2525584");
  encsToDelete.add("9be6b08c-704c-4ccb-bb0a-1fd305178583");
  encsToDelete.add("f285c7be-2c17-4b90-b7fa-1cc6efdf8835");
  encsToDelete.add("7db8cb8b-434e-49e1-88b0-efa74989e5a7");
  encsToDelete.add("408ae138-405d-406b-9e3a-92413d3a6b74");
  encsToDelete.add("bfb7be7e-5c8d-41da-b33e-96ccdef88226");
  encsToDelete.add("72af6a64-b3ab-42ae-aebe-c05f16d3fa03");
  encsToDelete.add("2c43a5d1-5e8b-40c9-a9da-1dadc51e5f30");
  encsToDelete.add("faad84f3-0365-42d2-9193-832f644ce2ef");
  encsToDelete.add("0408d3bb-4ce9-496a-b729-8c9d3eb11e3e");
  encsToDelete.add("d3f00105-eb63-44d2-b387-c9a896216317");
  encsToDelete.add("f40bc719-b640-4ccf-a51d-f982cd6d1f23");
  encsToDelete.add("86826aa6-1733-43f4-87b1-d05e956a5dcb");
  encsToDelete.add("ad3ecaf1-9898-4c7a-a6e0-baf32c060e11");
  encsToDelete.add("65b22f92-4760-48aa-8a1f-1bd63e0ebeee");
  encsToDelete.add("56da7fc3-2ce8-42cf-ac86-45f1ce93bc59");
  encsToDelete.add("701a263b-20e0-4ff5-9a7d-4afbe257e871");
  encsToDelete.add("0d014113-973e-4ae3-bf0a-8ab2c64d883f");
  encsToDelete.add("76073cae-bb93-40bf-ba32-c0a2c9c98ee8");
  encsToDelete.add("f3c64975-89ef-431b-9779-938933d9c9cd");
  encsToDelete.add("7765401a-7421-4fd4-9f9e-55f0d23465b8");
  encsToDelete.add("228a73e5-ad45-4db6-856c-f05ef0d6ce79");
  encsToDelete.add("eccb572a-344d-461c-803f-6965d8baf6d4");
  encsToDelete.add("4177dab9-70a1-4d54-82f0-b936e5d8a9e5");
  encsToDelete.add("6bbf284e-425d-451d-b67c-81daeb891f60");
  encsToDelete.add("82d32888-cfb6-431d-88fc-db921e4f50b0");
  encsToDelete.add("b9819bd3-eb80-4441-b9dc-a6011455c511");
  encsToDelete.add("d4417736-8d54-42d4-b297-aa98f899c32f");
  encsToDelete.add("74f990d7-12ef-4712-8bb4-138793909a09");
  encsToDelete.add("eadcf1b2-eaf2-435e-beaf-c2fef8cda677");
  encsToDelete.add("69b143e9-cb49-4a04-928f-d50c29d99123");
  encsToDelete.add("19654334-4367-4c55-9787-fda3786d8864");
  encsToDelete.add("6d6cabc9-fc5d-43f0-893b-de50119c4c81");
  encsToDelete.add("dc950101-76b6-41f0-97f0-15857cbaf0ee");
  encsToDelete.add("7d18a07c-4091-4b2e-8e42-45996f635c1f");
  encsToDelete.add("cce8d815-a6d4-49de-81a7-21fb9b00003c");
  encsToDelete.add("294543ac-f161-47ff-bdd1-bd028a823e06");
  encsToDelete.add("72d8888c-6e52-41f6-aff2-0e7ec400b5c0");
  encsToDelete.add("78482c78-e68c-4d04-b823-e6a07b72a951");
  encsToDelete.add("076ef914-d38f-43ca-8373-c5a32ddea054");
  encsToDelete.add("f3544378-6a1c-49f2-8f72-50af8b9c97e1");
  encsToDelete.add("cb0d0373-2220-44c1-92f0-5e3b05b3aa3e");
  encsToDelete.add("94e5edbe-814a-4e8b-9de2-d5e9edc3720c");
  encsToDelete.add("63289f81-6b69-4492-b5de-fe6b34fd012c");
  encsToDelete.add("c90a2ff8-84e0-4147-ac59-c38cc3e26963");
  encsToDelete.add("497fbb66-9575-4492-972a-2d11fc72061f");
  encsToDelete.add("609d911b-add0-4d0d-bfd2-6042d786886a");
  encsToDelete.add("a90d5df2-cb9f-4522-892c-6a33b3a1ba1c");
  encsToDelete.add("1c14dc5a-cc29-4e37-a732-a28a0406f63e");
  encsToDelete.add("8a3b6d76-8870-4ec1-bf4b-4fd0cbb9f917");
  encsToDelete.add("58c6214d-62fc-4fc1-8eac-4e416dbbb4e4");
  encsToDelete.add("8fae327e-df40-49ed-92ee-c665bf019406");
  encsToDelete.add("1b4af5ee-8ed4-4204-ae78-341d0c391337");
  encsToDelete.add("c64c3550-f949-452c-a3dd-27e2bf9fd958");
  encsToDelete.add("9ffbe5bc-de9e-4497-99f8-93837737bdfe");
  encsToDelete.add("75528b25-6c49-474f-91e6-2ad468d0c72d");
  encsToDelete.add("83c84d8e-48c3-459f-a20a-93601bd24be2");
  encsToDelete.add("bd0d090f-921c-4294-9bfc-e81500d96405");
  encsToDelete.add("8976dae6-805c-4ec6-a03a-2b3f0c655366");
  encsToDelete.add("f8be3f71-b070-4b49-af91-02f00234f6b0");
  encsToDelete.add("0f8b31f0-8312-43d7-bb02-d85e0531911d");
  encsToDelete.add("25e1c7b4-5cef-4f9f-b663-c50d037fd5b7");
  encsToDelete.add("3197ffc7-faba-46cb-920a-33fb24bccce5");
  encsToDelete.add("23db57f1-e18b-4a77-a109-9c3aa7c1d89d");
  encsToDelete.add("b1b84dae-4314-4cb0-aaab-e5a12eff9e6a");
  encsToDelete.add("73b713f7-9a59-47cf-bcc4-4a670584e5c5");
  encsToDelete.add("ba95720e-a7bb-48b3-8658-936b9f4c2ce2");
  encsToDelete.add("ca9511a4-aa38-4169-8045-92c66dd5fd27");
  encsToDelete.add("c7c66bf5-8ef0-42c7-9323-33b1af915a62");
  encsToDelete.add("89a90c28-e2fa-4c3d-bcc8-48744885cfe8");
  encsToDelete.add("8ac70837-e28b-49dd-8e67-ce997c8ead29");
  encsToDelete.add("0189f6c3-d706-4c90-aa4b-895d4e025f79");
  encsToDelete.add("400fffa3-c9f7-4822-9367-0bf41d006897");
  encsToDelete.add("7911c640-4736-41f6-a78d-a80f6668c956");
  encsToDelete.add("0886fb1f-00ef-48d8-bc47-3b30e203a67d");
  encsToDelete.add("7d79534c-eb3e-4977-9692-ebd9376930f3");
  encsToDelete.add("eff56837-78c4-4ae7-8339-4cb3266d578a");
  encsToDelete.add("a46e2790-96a3-4509-86cc-b4e67855e5e0");
  encsToDelete.add("d54e91b9-22ef-4f73-be74-272fc200b6b6");
  encsToDelete.add("7a462957-0768-4974-abb3-61cdb3498b54");
  encsToDelete.add("65ab5de8-29a8-4aa4-bd10-ab0e2e9d134b");
  encsToDelete.add("9396276c-8c55-4a12-a1d0-5923ac88fb68");
  encsToDelete.add("6f13914f-4b9a-4b30-a727-8f006155cb00");
  encsToDelete.add("3bfc2085-f601-4ee2-b7ce-52aa49a72f89");
  encsToDelete.add("22528675-2372-4cff-8ca5-35d4d5fde377");
  encsToDelete.add("9eba9793-c033-4bc4-b197-0ab6cca8b32d");
  encsToDelete.add("74e53573-f2f1-45f3-b8b0-5ead28431fc8");
  encsToDelete.add("fa6e694b-5a06-4ff1-8baf-1895727af6fa");
  encsToDelete.add("2b7df231-1962-4970-8e3d-2f59041f6ea5");
  encsToDelete.add("df35a646-f3c7-4d7f-a825-232c756a9d9b");
  encsToDelete.add("60422db4-111e-40cd-8e0a-33aea5d5ea4c");
  encsToDelete.add("3df87ca3-4dde-4e03-948c-d4f4be8525db");
  encsToDelete.add("50d315c1-01ae-440c-8d98-4f93c0771743");
  encsToDelete.add("f096e904-cd60-4069-b828-3aa8edb4bb71");
  encsToDelete.add("3059b50a-bf2d-49b8-8746-cb15985875f1");
  encsToDelete.add("2afc0ec9-b505-43d8-9855-6a519beba935");
  encsToDelete.add("0e22e7c4-f2a9-488a-a444-25e5b5302928");
  encsToDelete.add("792e58f1-d810-4bb3-882d-74902d3a6900");
  encsToDelete.add("c952f087-e6c8-4752-bc20-d54273336ee1");
  encsToDelete.add("eab0fc01-9b8f-4e19-a12c-612fbd662e24");
  encsToDelete.add("e765ce16-90ad-4d16-bf81-0622fd095e94");
  encsToDelete.add("aa46809b-5080-4627-be7c-1bac67f039d0");
  encsToDelete.add("6b21ea63-3635-44b1-a0c8-d2c6c98037e4");
  encsToDelete.add("c854a9f6-f373-41bc-80fd-6443cd1cdf97");
  encsToDelete.add("75aaee36-dbca-4d4e-8375-52159c1ba5dd");
  encsToDelete.add("eaf7b9e2-c161-4ba7-beae-0dd66d16a84f");
  encsToDelete.add("399a6b6d-539f-44cf-9b2d-1b774a278db2");
  encsToDelete.add("3ec921f5-5448-4df8-9a01-7f62735eaf6e");
  encsToDelete.add("2f6606f8-80c6-40d0-897a-e5cf6d53d830");
  encsToDelete.add("347ed4f7-4901-4180-9637-096d6535791d");
  encsToDelete.add("c57c01a7-666e-40b6-8815-511cb03172f0");
  encsToDelete.add("2c51f62f-963d-4791-908b-5d68ebff4e9d");
  encsToDelete.add("1701b131-117d-4aff-8f86-3ade11adee2e");
  encsToDelete.add("94a31c6e-ee42-41c5-9fc9-ea880a1e7303");
  encsToDelete.add("90f8b05e-4aa7-4db2-9240-ad2132fbc3a8");
  encsToDelete.add("1d98daf6-7dcf-4fdb-8b96-f10d688051a3");
  encsToDelete.add("5bcfdf31-78ad-4737-a6fe-040935745ce2");
  encsToDelete.add("f60f5b05-a4e7-4a06-b1bd-4cbde4e29197");
  encsToDelete.add("1d4b9e3c-bd04-495c-a6f1-439674fb8b4b");
  encsToDelete.add("3240d913-5b14-44cd-baa8-b67055832822");
  encsToDelete.add("14476d68-0d2c-49a7-887e-e050c74df2c0");
  encsToDelete.add("c7a1d8e8-049c-4a73-9700-120fd79dc670");
  encsToDelete.add("bf927952-90a2-4155-9245-e2a2184d3816");
  encsToDelete.add("2f91ecb8-4204-4fa0-a084-7c765abd2342");
  encsToDelete.add("1f3d6639-61af-410b-a9c8-f7e342831602");
  encsToDelete.add("39295982-0707-458e-9370-cdd812b54f16");
  encsToDelete.add("168e9f71-5474-4589-8b3e-16ae89145987");
  encsToDelete.add("21e47c01-4bbd-41ec-bd36-6d2103968afa");
  encsToDelete.add("e3c796a9-a397-4383-8229-2175af439e62");
  encsToDelete.add("26e7c0f3-a2e2-4d59-84b9-8d1c45635acc");
  encsToDelete.add("946b6a5a-3ca5-4ef4-8d62-367b52876abd");
  encsToDelete.add("a44d29a7-c94f-4b32-8e73-4d436e981ea3");
  encsToDelete.add("5ab06495-f0b2-47ae-840f-74481918f731");
  encsToDelete.add("494ac67f-d938-4a10-95fe-9f2ae56ee0c7");
  encsToDelete.add("4bc21dfe-1c55-43a8-9af5-d655f7fe037f");
  encsToDelete.add("5505eced-9824-4a98-95fc-6530cd7dbf32");
  encsToDelete.add("27d28114-fe81-4ee1-9c8a-c02a444b45fd");
  encsToDelete.add("abde5784-acb8-427f-a6db-1c8441d7f022");
  encsToDelete.add("1be05c94-f91a-42a9-bcce-e5c23c672dcb");
  encsToDelete.add("bdb79619-7ee9-49b5-9431-4b56e835bda2");
  encsToDelete.add("161757b4-dc2a-4152-9b4b-d40e8e7eada6");
  encsToDelete.add("e596ac86-be4a-4bf8-a353-5630750fe7fc");
  encsToDelete.add("c4b146a5-3752-4e9b-b2ec-21c6596f90c0");
  encsToDelete.add("d6aa0139-1fdc-4c6f-ab1e-7ebe2f5793e1");
  encsToDelete.add("96c0dcd9-9a92-421f-8364-d17517c012c5");
  encsToDelete.add("7679c263-e9f6-4a18-858f-4a690cd4c518");
  encsToDelete.add("8508a14f-11d7-4a06-8259-93d452ec78b3");
  encsToDelete.add("7ba94f57-bace-429a-ae4e-553e39c88a7c");
  encsToDelete.add("0a76889c-dd0f-4a70-adf4-37d2132a108c");
  encsToDelete.add("18a4731f-9984-4a1b-9cc2-dac49f6c1b2a");
  encsToDelete.add("053c823b-4299-45e7-ba73-be62f0ac6be1");
  encsToDelete.add("983e0d55-f473-4abf-9b23-2e2aad9a3dee");
  encsToDelete.add("0b0b7608-311b-4563-b574-9c6b834f1cc0");
  encsToDelete.add("85a4e4fb-dd85-4aae-a82f-d7ae68c990d7");
  encsToDelete.add("a1884c77-d27c-4d45-aae8-27854d41350d");
  encsToDelete.add("2d5eca8e-f97a-4451-a2b3-333339466443");
  encsToDelete.add("c68f1a3c-d845-4bfb-8a3e-2c699afd7765");
  encsToDelete.add("578bb5fa-f3ef-46cd-9cac-e74bf1aa24ee");
  encsToDelete.add("14aefe74-6541-4fdf-b31c-e7d3881c54c9");
  encsToDelete.add("ae08348d-9332-45bc-8a68-66582e1359d2");
  encsToDelete.add("90972db0-fb04-4080-b9bb-512b18c7c310");
  encsToDelete.add("2d4482e2-eaaa-4206-ba9d-e8dec6be7c68");
  encsToDelete.add("319249d5-e2ce-457f-a5cd-7bfe50afabc5");
  encsToDelete.add("1f0bc3d3-8e1f-4a9b-84df-19fbd8db847f");
  encsToDelete.add("66bcc5c0-c769-40df-9d6f-b48baeb1aef1");
  encsToDelete.add("8aba14af-9345-4077-9f2a-c6d48ffc820e");
  encsToDelete.add("2e8d769c-7274-4e91-ad2c-27ba2c59ac72");
  encsToDelete.add("4029b159-62e6-4d79-ba63-05c23b4ea280");
  encsToDelete.add("952c3693-c9bc-4ff8-b10d-d674ce1c83ba");
  encsToDelete.add("776b0db2-9e60-4646-b1bc-94d6356f7dbc");
  encsToDelete.add("63928b8a-996b-430a-a6ed-e45ed724c633");
  encsToDelete.add("32d03a0c-aeb5-4767-a3b3-3d8c7c9c603b");
  encsToDelete.add("11e7b81c-d193-46ca-93a8-ec85d21772e0");
  encsToDelete.add("d958de46-46b6-4d34-80fa-7623b504fea8");
  encsToDelete.add("6c0f9845-49b8-4331-8298-131eeae7eaa5");
  encsToDelete.add("b6846d70-d57e-42fb-938a-d7c75821c6c1");
  encsToDelete.add("51d12006-48d7-40d3-a5d4-a0eae659d8ad");
  encsToDelete.add("79b1b165-dc00-4979-ab01-49573966245d");
  encsToDelete.add("bf5014d7-d9af-41ed-abb6-d2323cc638e3");
  encsToDelete.add("849ead95-932d-4afc-9871-15ea4c53278d");
  encsToDelete.add("558da5c6-e46d-4486-9455-59bcf9f4e036");
  encsToDelete.add("520299da-e7c6-47f1-8cee-7d9f7cca6af4");
  encsToDelete.add("db33101d-b6c2-4a4b-a519-05681ccaf343");
  encsToDelete.add("54a7c3c2-1835-4851-b800-df828dc33373");
  encsToDelete.add("b6fe1752-0fbc-488a-8271-69c045a14e4f");
  encsToDelete.add("566ce6b4-7c2a-4116-b60c-ace2069fed33");
  encsToDelete.add("481c92c0-429f-419d-9a27-7b75c87af28e");
  encsToDelete.add("a42d1220-4b10-4f1c-a108-9bf9cf25b94c");
  encsToDelete.add("d7c1a21e-d741-40ea-9060-e0805325849d");
  encsToDelete.add("0a7e4320-a57d-46f2-9631-b0b60f2b2f92");
  encsToDelete.add("3f38874f-848c-4705-8b46-1f618da1eaf2");
  encsToDelete.add("d6e67b89-3459-40e1-952e-9a3beb3d42eb");
  encsToDelete.add("fc01af01-0dbe-45fc-8c1d-c1ec2d3c158a");
  encsToDelete.add("24e8a44e-d4c3-4116-a8c3-b12c8e106699");
  encsToDelete.add("57e7b786-95af-41ab-a406-be10abd79f1f");
  encsToDelete.add("b3cd9b4e-6bab-4d47-b1fe-d7c489b5b733");
  encsToDelete.add("0fc01281-a30d-4938-a760-1c57f7c24de1");
  encsToDelete.add("a726f938-785a-4fad-badb-3979dd417821");
  encsToDelete.add("4c6fae61-b5cc-41be-a0cd-332ce1cb5cd8");
  encsToDelete.add("70af0913-13ec-40b4-b438-236994b00364");
  encsToDelete.add("fd37c5a5-fe04-48e5-93b6-346505cffc1a");
  encsToDelete.add("17e9cdd7-8fbb-45a9-92be-971a9fe591a0");
  encsToDelete.add("581bb95c-0f84-4e9a-a649-160544c5fc0a");
  encsToDelete.add("14d6a816-a981-40cc-8853-bb6928fbc9ad");
  encsToDelete.add("4e2ecf98-d381-4863-93a2-80a253747336");
  encsToDelete.add("9ad69bdc-2951-4b17-8311-2ccd54d58958");
  encsToDelete.add("5284ca01-3b52-418b-b1cb-ee91b7542372");
  encsToDelete.add("6188d7af-69c2-41ca-89e1-3e6914ac14f5");
  encsToDelete.add("5de7d467-38bf-4e35-a428-175871a60775");
  encsToDelete.add("58439614-539e-4cb4-a143-bdea8af6935a");
  encsToDelete.add("8ec9e25e-74c9-463d-87b6-b82bb7955528");
  encsToDelete.add("c36cccef-1584-4bda-938c-ecdea99362e4");
  encsToDelete.add("2aff44c3-79b7-4265-bd62-2f6eee0ef119");
  encsToDelete.add("0d885a8b-f224-4f5f-a27c-096e8a31849c");
  encsToDelete.add("5635f49b-4a79-4235-9a62-3c345f7d8bac");
  encsToDelete.add("dcf965db-c570-4bd0-b22e-9a65d326424d");
  encsToDelete.add("d2484376-91a9-494a-bf60-757b981922ee");
  encsToDelete.add("adb5b60f-34d0-42a2-ad11-bc491c268579");
  encsToDelete.add("b701099d-14f3-46af-b3da-e463a199afe2");
  encsToDelete.add("e3c1af99-27b8-494d-a79e-01a4c3ae45eb");
  encsToDelete.add("ad78affb-c6c7-416f-a228-f3a7d9b7f930");
  encsToDelete.add("2351724d-0b79-493f-94af-91470611e4a3");
  encsToDelete.add("024f3665-2c3e-4b42-8398-f67a96a053e1");
  encsToDelete.add("67df4f4a-77fb-4068-8139-a0eaa2acc7ec");
  encsToDelete.add("624f3143-fcd1-474c-8eb4-c3cbf5766404");
  encsToDelete.add("5fe0e8cf-4410-435a-abb8-3c56e91be309");
  encsToDelete.add("72cd1745-2252-4426-88b4-02b286f2b230");
  encsToDelete.add("c24fa4b0-0a64-4ec2-97a3-f801c31e8763");
  encsToDelete.add("f14b796e-f1a2-4c91-ac9a-f3073ce40111");
  encsToDelete.add("c0e17651-adf9-44cb-a518-7254485c798f");
  encsToDelete.add("81907307-fbc2-47c6-bc2b-8e83123b25ae");
  encsToDelete.add("069c2b12-b4b8-4a0c-a474-956d8cd66d5d");
  encsToDelete.add("db8b3ec7-f82b-4110-abdb-c2887ca3e912");
  encsToDelete.add("04ce3e62-eaa2-489d-9f5b-4ef26fe91eec");
  encsToDelete.add("fe9ba317-0cc2-4208-a354-86632f580d3b");
  encsToDelete.add("1f3c1dbb-63c5-45e2-a60c-166f469509b0");
  encsToDelete.add("f9102afb-5bd6-4bd9-ab54-fb8c4d27f2d8");
  encsToDelete.add("6112d9e9-f027-4086-9694-b9ac3f110cd0");
  encsToDelete.add("8c2febd8-bce8-41db-8a6b-df204301745c");
  encsToDelete.add("d324a6ef-f9c9-4c40-828f-53cfc3900374");
  encsToDelete.add("0d4fc4c4-143a-43f8-9bca-bd03e215e226");
  encsToDelete.add("95944d62-ac3a-4975-a835-ae49b34ac75d");
  encsToDelete.add("4802d50d-0e4b-4819-82e2-c156568117c4");
  encsToDelete.add("fe4a2769-0d23-4ba4-a970-6c1fc0c99310");
  encsToDelete.add("0aa56799-5aa7-4f44-bf8d-fc0a02f9c1ea");
  encsToDelete.add("df09cd12-7293-4f6d-a26b-cb954f009f47");
  encsToDelete.add("c2977d9a-d5b7-4597-b334-2794c30e8686");
  encsToDelete.add("44aed10d-81db-416c-848d-b7145c756006");
  encsToDelete.add("fa8e3936-5a67-4499-904b-fb629eb9e345");
  encsToDelete.add("c1698a52-1b84-4c79-88fe-12a3dc491cf4");
  encsToDelete.add("803c5bef-1f54-4e21-9191-2965abba4c4b");
  encsToDelete.add("b591deec-ce7a-4b51-9cac-448a12cd1d5c");
  encsToDelete.add("97cd0c45-be09-4945-9875-85f5650419e1");
  encsToDelete.add("4e429484-30e4-491f-a1ae-0335abe14988");
  encsToDelete.add("2291c174-bf42-49ba-b91a-145c6708e331");
  encsToDelete.add("c2dac0ec-cc41-47b8-adf0-c40b9192b089");
  encsToDelete.add("6e3047e2-30cf-4e3a-86f5-13d65acde15a");
  encsToDelete.add("dda9d7c4-a72e-4c8c-bd41-dc22a351b25e");
  encsToDelete.add("ce116f07-dc44-4f41-8264-d4a4c205d4b0");
  encsToDelete.add("3bf7c9d9-3e99-4202-99b3-a0c043474521");
  encsToDelete.add("690aa1f8-3523-4a0a-97c4-bfe46b980cfc");
  encsToDelete.add("2149af42-a168-478c-8389-58a298140ce0");
  encsToDelete.add("28717260-658c-4dea-96b4-8d434e0b3feb");
  encsToDelete.add("24c4903e-5236-4272-b3ac-8dbbd6d8af87");
  encsToDelete.add("aae076b4-c6b9-4196-ab50-cb32f2ddb96c");
  encsToDelete.add("0745edf7-e300-48b7-a806-87cc20a257db");
  encsToDelete.add("f62c008f-e3b8-4b0b-af01-e3ddf46aad74");
  encsToDelete.add("6cc2b85d-72cf-4a83-b5fa-fbc01e3da63c");
  String rootWebappPath = getServletContext().getRealPath("/");
  File webappsDir = new File(rootWebappPath).getParentFile();
  File shepherdDataDir = new File(webappsDir, CommonConfiguration.getDataDirectoryName(context));
  if(!shepherdDataDir.exists()){shepherdDataDir.mkdirs();}
  boolean locked = false;
  for(int j=0; j<encsToDelete.size(); j++){
    Encounter enc2trash = myShepherd.getEncounter(encsToDelete.get(j));
        myShepherd.commitDBTransaction();
      	myShepherd.beginDBTransaction();
        // setDateLastModified(enc2trash);
        if (enc2trash.getIndividualID()==null) {
          //myShepherd.beginDBTransaction();
          try {
            System.out.println("deleteMe got here a1");
            Encounter backUpEnc = myShepherd.getEncounterDeepCopy(enc2trash.getEncounterNumber());
            String savedFilename = "from_delete_script" + ".dat";
            System.out.println("deleteMe savedFilename is: " + savedFilename);
            File thisEncounterDir = new File(Encounter.dir(shepherdDataDir, "from_delete_script"));
            if(!thisEncounterDir.exists()){
              thisEncounterDir.mkdirs();
              System.out.println("Trying to create the folder to store a dat file in EncounterDelete2: "+thisEncounterDir.getAbsolutePath());
              File serializedBackup = new File(thisEncounterDir, savedFilename);
              FileOutputStream fout = new FileOutputStream(serializedBackup);
              ObjectOutputStream oos = new ObjectOutputStream(fout);
              oos.writeObject(backUpEnc);
              oos.close();
            }
            System.out.println("deleteMe got here a2");
          } catch (NotSerializableException nse) {
            System.out.println("deleteMe got here a3");
            System.out.println("[WARN]: The encounter "+enc2trash.getCatalogNumber()+" could not be serialized.");
            nse.printStackTrace();
          }
          try {
            System.out.println("deleteMe got here a4");
            Occurrence occ = myShepherd.getOccurrenceForEncounter(enc2trash.getID());
            if (occ==null&&(enc2trash.getOccurrenceID()!=null)&&(myShepherd.isOccurrence(enc2trash.getOccurrenceID()))) {
              System.out.println("deleteMe got here a4.1");
              occ = myShepherd.getOccurrence(enc2trash.getOccurrenceID());
            }
            if(occ!=null) {
              System.out.println("deleteMe got here a4.2");
              occ.removeEncounter(enc2trash);
              enc2trash.setOccurrenceID(null);

              //delete Occurrence if it's last encounter has been removed.
              if(occ.getNumberEncounters()==0){
                System.out.println("deleteMe got here a4.3");
                myShepherd.throwAwayOccurrence(occ);
              }

              myShepherd.commitDBTransaction();
              myShepherd.beginDBTransaction();
              System.out.println("deleteMe got here a4.4");
            }
            List<Project> projects = myShepherd.getProjectsForEncounter(enc2trash);
            if (projects!=null&&!projects.isEmpty()) {
              System.out.println("deleteMe got here a4.5");
              for (Project project : projects) {
                project.removeEncounter(enc2trash);
                myShepherd.updateDBTransaction();
              }
            }
            //Remove it from an ImportTask if needed
            ImportTask task=myShepherd.getImportTaskForEncounter(enc2trash.getCatalogNumber());
            if(task!=null) {
              System.out.println("deleteMe got here a4.6");
              task.removeEncounter(enc2trash);
              task.addLog("Servlet EncounterDelete removed Encounter: "+enc2trash.getCatalogNumber());
              myShepherd.updateDBTransaction();
            }
            if (myShepherd.getImportTaskForEncounter(enc2trash)!=null) {
              System.out.println("deleteMe got here a4.7");
              ImportTask itask = myShepherd.getImportTaskForEncounter(enc2trash);
              itask.removeEncounter(enc2trash);
              myShepherd.commitDBTransaction();
              myShepherd.beginDBTransaction();
            }
            //Set all associated annotations matchAgainst to false
            enc2trash.useAnnotationsForMatching(false);
            //break association with User object submitters
            if(enc2trash.getSubmitters()!=null){
              System.out.println("deleteMe got here a4.8");
              enc2trash.setSubmitters(null);
              myShepherd.commitDBTransaction();
              myShepherd.beginDBTransaction();
            }
            //break asociation with User object photographers
            if(enc2trash.getPhotographers()!=null){
              System.out.println("deleteMe got here a4.9");
              enc2trash.setPhotographers(null);
              myShepherd.commitDBTransaction();
              myShepherd.beginDBTransaction();
            }
            //record who deleted this encounter
            System.out.println("deleteMe request.getRemoteUser() is: " + request.getRemoteUser());
            enc2trash.addComments("<p><em>" + request.getRemoteUser() + " on " + (new java.util.Date()).toString() + "</em><br>" + "Deleted this encounter from the database.");
            myShepherd.commitDBTransaction();
            System.out.println("deleteMe got here a4.91");
            ArrayList<Annotation> anns = enc2trash.getAnnotations();
            for (Annotation ann : anns) {
              System.out.println("deleteMe got here a4.92");
              myShepherd.beginDBTransaction();
              enc2trash.removeAnnotation(ann);
              myShepherd.updateDBTransaction();
              List<Task> iaTasks = Task.getTasksFor(ann, myShepherd);
              if (iaTasks!=null&&!iaTasks.isEmpty()) {
                for (Task iaTask : iaTasks) {
                  System.out.println("deleteMe got here a4.93");
                  iaTask.removeObject(ann);
                  myShepherd.updateDBTransaction();
                }
              }
              myShepherd.throwAwayAnnotation(ann);
              myShepherd.commitDBTransaction();
              System.out.println("deleteMe got here a4.94");
            }
            //now delete for good
            System.out.println("deleteMe got here a5");
            myShepherd.beginDBTransaction();
            myShepherd.throwAwayEncounter(enc2trash);
            System.out.println("deleteMe got here a6");
            //remove from grid too
            GridManager gm = GridManagerFactory.getGridManager();
            gm.removeMatchGraphEntry("from_delete_script");
            myShepherd.commitDBTransaction();
            System.out.println("deleteMe got here a7");
            System.out.println("Removing encounter " + "from_delete_script" + " from the database.");
            out.println(ServletUtilities.getHeader(request));
            out.println("<strong>Success:</strong> I have removed encounter " + "from_delete_script" + " from the database. If you have deleted this encounter in error, please contact the webmaster and reference encounter " + "from_delete_script" + " to have it restored.");
            List<String> allStates=CommonConfiguration.getIndexedPropertyValues("encounterState",context);
            int allStatesSize=allStates.size();
            if(allStatesSize>0){
              for(int i=0;i<allStatesSize;i++){
                String stateName=allStates.get(i);
                out.println("<p><a href=\"encounters/searchResults.jsp?state="+stateName+"\">View all "+stateName+" encounters</a></font></p>");
              }
            }
            out.println(ServletUtilities.getFooter(context));
            System.out.println("deleteMe got here a8");
          } catch (Exception edel) {
            locked = true;
            //log.warn("Failed to serialize encounter: " + "from_delete_script", edel);
            edel.printStackTrace();
            myShepherd.rollbackDBTransaction();
            System.out.println("deleteMe got here a9");
          }
          // Notify new-submissions address
          Map<String, String> tagMap = NotificationMailer.createBasicTagMap(request, enc2trash);
          tagMap.put("@USER@", request.getRemoteUser());
          tagMap.put("@ENCOUNTER_ID@", "from_delete_script");
          String mailTo = CommonConfiguration.getNewSubmissionEmail(context);
          NotificationMailer mailer = new NotificationMailer(context, null, mailTo, "encounterDelete", tagMap);
          ThreadPoolExecutor es = MailThreadExecutorService.getExecutorService();
          es.execute(mailer);
          es.shutdown();
          System.out.println("deleteMe got here a10");
        }
        else {
          System.out.println("deleteMe got here a11");
          myShepherd.rollbackDBTransaction();
          out.println(ServletUtilities.getHeader(request));
          out.println("Encounter " + "from_delete_script" + " is assigned to a Marked Individual and cannot be deleted until it has been removed from that individual.");
          out.println("<p><a href=\"http://" + CommonConfiguration.getURLLocation(request) + "/encounters/encounter.jsp?number=" + "from_delete_script" + "\">Return to encounter " + "from_delete_script" + "</a>.</p>\n");
          response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
          out.println(ServletUtilities.getFooter(context));
          System.out.println("deleteMe got here a12");
        }
      }
      // else {
      //   myShepherd.rollbackDBTransaction();
      //   out.println(ServletUtilities.getHeader(request));
      //   out.println("<strong>Error:</strong> I don't know which encounter you're trying to remove.");
      //   out.println(ServletUtilities.getFooter(context));
      //   response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
      // }
      // myShepherd.closeDBTransaction();
      // out.close();
    // }
    // }

  } catch(Exception e){
    System.out.println("deleteMe error with trying to delete encounters: ");
    e.printStackTrace();
  }
      finally{
        System.out.println("deleteMe got here a13");
        myShepherd.rollbackDBTransaction();
      	myShepherd.closeDBTransaction();
        out.close();
        System.out.println("deleteMe got here a14");
      }

    %>
