-- D-0.6: UUIDv7 gerado no banco. Nesta fatia toda linha nasce no servidor, entao um gerador so
-- e suficiente e nao ha risco de divergencia. Quando o Android precisar gerar IDs offline
-- (fatia 4), o gerador Kotlin/KMP entra como decisao daquela fatia.
--
-- Layout (RFC 9562): 48 bits de timestamp em milissegundos, nibble de versao 7, variante 10xx,
-- restante aleatorio. set_byte/get_byte sao usados em vez de set_bit porque a numeracao de bytes
-- e inequivoca (indice 0 a esquerda), o que torna a implementacao verificavel por leitura.

create or replace function public.uuid_generate_v7()
returns uuid
language plpgsql
volatile
as $$
declare
    v_bytes bytea;
    v_millis bigint;
begin
    v_millis := floor(extract(epoch from clock_timestamp()) * 1000)::bigint;

    -- Os 6 bytes mais significativos passam a ser o timestamp; o resto permanece aleatorio.
    v_bytes := overlay(
        uuid_send(gen_random_uuid())
        placing substring(int8send(v_millis) from 3 for 6)
        from 1 for 6
    );

    -- Byte 6: nibble alto = versao 7.
    v_bytes := set_byte(v_bytes, 6, (get_byte(v_bytes, 6) & 15) | 112);
    -- Byte 8: dois bits altos = variante RFC 4122 (10xx).
    v_bytes := set_byte(v_bytes, 8, (get_byte(v_bytes, 8) & 63) | 128);

    return encode(v_bytes, 'hex')::uuid;
end;
$$;

comment on function public.uuid_generate_v7() is
    'Gera UUIDv7 (D11). Ordenavel por tempo, o que preserva localidade de indice.';
