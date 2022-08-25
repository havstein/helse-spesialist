-- Disse dataene ble ikke lagret da overstyring ble laget. Vi ønsker å lage fremmednøkkel fra overstyring til hendelse,
-- og trenger derfor radene. Det skal la seg gjøre å gjenskape innholdet i json fra de andre tabellene ved behov.

INSERT INTO hendelse (id, type, fodselsnummer, data)
SELECT o.hendelse_id,
       'OVERSTYRING',
       p.fodselsnummer,
       '{
         "message": "Til hvem det måtte angjelde: Disse dataene ble ikke lagret da overstyring ble laget. Vi ønsker å lage fremmednøkkel fra overstyring til hendelse, og trenger derfor radene. Det skal la seg gjøre å gjenskape innholdet i json fra de andre tabellene ved behov."
       }'::json
FROM overstyring o
         JOIN person p ON p.id = o.person_ref
WHERE o.hendelse_id in (
                      '63de7175-0585-432b-adf8-c9b50ee0e077',
                      'bb2b7d24-cae7-4630-9f84-11f3e33e6856',
                      'fe992607-7aa1-47c2-8844-eae8b854ff3a',
                      '7ca04e7b-38e1-41da-a909-192fadd5d30a',
                      '0023f9da-a16a-4d17-989e-464dd567be52',
                      '7ea4f9d3-1868-4c07-a857-8909350e9994',
                      '8b3b1905-7853-437d-a9fe-8977fac8ef0d',
                      '08801aaf-8dd6-4c38-9ece-b593399eac20',
                      '587e2378-bf5c-462d-bdac-e7dd5dbf0738',
                      '6686ab95-c06e-426d-b04b-9f064d458f7a',
                      'd439262d-808f-4f67-9dfd-cb731aab5074',
                      '1edba99e-60f3-4e3b-9754-de1284a8e28c',
                      '8b9f51a2-41e5-4399-9ace-70122860cf90',
                      'ea9fa00d-5cb9-4299-9e36-4e532ad8a181',
                      'b209e782-e96e-4da3-a471-5d1f79cd7dbb',
                      '77de349b-1d2e-4202-9b8c-02c7e9eb6d90',
                      'ab976833-0fe4-40d3-b4b6-c1fff0e2550a',
                      '4495fbe3-d162-4503-bf02-08565ad28125',
                      '3e84120f-4447-49bc-a35c-451bb2ce8eb6',
                      '0806ecec-132d-42bc-bf7e-820226a26ec2',
                      'e74b9e28-47c9-419e-83fe-00c548155c78',
                      '94424779-4d44-457b-a102-76e69141f4d6',
                      'da741f8f-61df-4a93-b7ea-ecc7aa730a4e',
                      '53a4b96e-e8f7-466d-a73b-e5780481a67d',
                      '953ae174-ca84-4463-a7cc-b580bd2ce398',
                      'f56d6810-faad-4ba9-9605-82f5d527daf4',
                      'd80dce24-8fe4-48ff-b50e-3c398c776414',
                      'f26837e3-c3b9-4cc1-a10b-d2078410dce7',
                      '9d404248-17ee-46e5-866b-a156eefd4ff8',
                      '8ba7ebb3-b757-4041-9795-9e8fe6608c1f',
                      '1ba3a772-859f-45b0-b48e-df21a1a589b8',
                      '645219d1-550d-46d3-967d-2935fa2a0f1d',
                      '5221b6c4-7168-43dd-abe3-5068041ede99',
                      '688a5fe2-869f-4c87-ae78-e376f7781c60',
                      '8fc4c1c8-6e4f-489c-be80-f61c85f4db1b',
                      '478a0ee5-059d-45dd-a136-47e6d61a75ff',
                      '8f3c8502-1a71-4548-ae87-df45f2a51aa5',
                      '9dfbe128-26cb-4d25-aa0d-a71aba0d4010',
                      'b0f407c0-46b0-4860-ac81-5a317f5553bf',
                      '36d7ef22-d97b-402b-a57f-591c169d89ef',
                      'ba7f71a4-de20-4246-8399-e5257e85895b',
                      'e2e9b3f5-aa7e-4d62-85bf-bb509e8a112d',
                      'adc4458c-287c-4557-b16e-509a0570552f',
                      '34372111-7d82-4ebf-8b9a-fcde0e14ebec',
                      '41b4e149-e4a5-43a1-92f4-de582a58623b',
                      '071781e4-d30d-4a43-9e6f-a69337cb44e7',
                      '1a34b452-6dc0-4aa4-b0d0-99a53dc82486',
                      '725feaf1-8b57-44bd-b41c-47333384eb52',
                      'd621153e-c77a-4bc5-9245-3f7d48167a9f',
                      '0056fd2e-8fb8-4ecb-ae7c-fa48acd558df',
                      '47213a2a-c502-4420-8437-28a9a000c60d',
                      '321c9586-3531-4da1-ba33-a7dad26b286b',
                      'c4c6dfe9-ab14-4a62-8587-459592848d5d',
                      'a0c0c18d-a23a-4ef0-84ce-eb30c2c42d1d',
                      '0e75017c-5ce1-46fc-8a64-ebd146271225',
                      '4f101f0d-b1b2-4dff-8357-5d3b5e6e55be',
                      '71d21e57-1d11-4ee3-bfee-e13701860790',
                      'ba472757-a479-4e6e-baba-cf2048d0ee24',
                      'fc44f1e8-0ade-42aa-8c24-356cfde60723',
                      '59937a8f-bb09-4c72-a6f4-609c82611a64',
                      '77c9d4fa-b4c8-4aae-a549-224f64d0c4ef',
                      'b1fe075e-2fb8-4739-8e07-a9602f3bcb84',
                      '890d2348-3a84-48ad-a0b7-6ad9cee69570',
                      '195aaad1-09c5-4801-ae4b-da124e9a9ee1',
                      '7064e5b1-1c0a-4784-a637-7f810556c524',
                      '6b1e1c99-b742-47b3-845b-9b8fa34944ae',
                      'ea48e0dc-20f0-460c-8735-9e01f6ec3ec2',
                      '681d8396-7069-4a5e-bd82-e7609c7ff113',
                      '9cf7cd34-23d8-449a-89e6-aa2021b3811c',
                      'af1f9726-f3b3-4ede-a8bc-0fedd597b341',
                      '5cf72236-e501-4ccc-b9d7-153e47ffbd1a',
                      'de175e00-1050-40d3-99e4-3a5d61d4d238',
                      '61ce763f-d751-49fb-9d06-b3259f486170',
                      'aa118603-7f5b-4c23-83cc-d6728fece7bf',
                      'd931f592-e9f8-4504-86e1-52f95b6005c3',
                      '20ea8a46-e288-41c6-b61b-ce5f4342e973',
                      'dd0c14d2-deff-43b3-9d2d-e272bcbad5e9'
    );